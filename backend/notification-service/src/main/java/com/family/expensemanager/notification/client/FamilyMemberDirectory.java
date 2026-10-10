package com.family.expensemanager.notification.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import com.family.expensemanager.common.security.JwtUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Looks up the members of a family (id + email + display name) from auth-service, which owns USERS —
 * notification-service has no other way to know a family member's email address. This is the one
 * synchronous cross-service call in the notification path, so it fails soft: any error is logged and
 * treated as "no recipients", so an auth-service hiccup can never make Kafka redeliver an event
 * (and re-insert its in-app notification) forever.
 *
 * Resilience (config under resilience4j.* in application.yml, instance "auth-service"): a network error or
 * 5xx is retried once after a short pause; a circuit breaker stops calling auth-service for a while once most
 * recent calls failed, so a down auth-service costs no timeout per event — both end in the empty-list fallback.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "FamilyMemberDirectory")
public class FamilyMemberDirectory {

    private static final String ROLE_SERVICE = "SERVICE";
    private static final long TOKEN_TTL_MILLIS = 60_000;

    /** {@code role}: the member's role in that family (OWNER, MEMBER, VIEWER, CHILD); null from an older auth-service. */
    public record Member(Long userId, String email, String displayName, String role) {

        // Explicit because the shorter constructor below would otherwise leave Jackson's creator choice ambiguous.
        @JsonCreator
        public Member {
        }

        public Member(Long userId, String email, String displayName) {
            this(userId, email, displayName, null);
        }
    }

    private record Envelope(List<Member> data) {
    }

    private final RestClient restClient;
    private final JwtUtil jwtUtil;

    /**
     * Connect/read timeouts: without them a hung auth-service blocked the Kafka listener thread calling this,
     * stalling every notification behind it. On timeout the call fails soft like any other error below.
     */
    public FamilyMemberDirectory(RestClient.Builder builder, JwtUtil jwtUtil,
                                 @Value("${app.auth-service-url}") String authServiceUrl,
                                 @Value("${app.auth-service-connect-timeout:2s}") Duration connectTimeout,
                                 @Value("${app.auth-service-read-timeout:5s}") Duration readTimeout) {
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        this.restClient = builder.baseUrl(authServiceUrl)
                .requestFactory(ClientHttpRequestFactories.get(timeouts))
                .build();
        this.jwtUtil = jwtUtil;
    }

    // Retry wraps CircuitBreaker (Resilience4j's aspect order), so the fallback sits on @Retry: on the breaker
    // it would swallow the error before Retry ever saw it. An open breaker is not retried (see application.yml).
    @Retry(name = "auth-service", fallbackMethod = "membersUnavailable")
    @CircuitBreaker(name = "auth-service")
    public List<Member> listMembers(Long familyId) {
        String token = jwtUtil.generateToken("notification-service", Map.of(JwtUtil.CLAIM_ROLE, ROLE_SERVICE),
                TOKEN_TTL_MILLIS);
        Envelope envelope = restClient.get()
                .uri("/internal/families/{familyId}/members", familyId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Envelope.class);
        return envelope == null || envelope.data() == null ? List.of() : envelope.data();
    }

    /** Fail-soft fallback for {@link #listMembers}: whatever went wrong, the event is handled with no recipients. */
    @SuppressWarnings("unused") // invoked reflectively by Resilience4j
    private List<Member> membersUnavailable(Long familyId, Throwable cause) {
        log.warn("Không lấy được danh sách thành viên gia đình familyId={}: {}", familyId, cause.toString());
        return List.of();
    }
}
