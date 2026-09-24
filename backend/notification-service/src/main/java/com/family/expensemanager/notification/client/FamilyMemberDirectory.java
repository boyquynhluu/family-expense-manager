package com.family.expensemanager.notification.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.family.expensemanager.common.security.JwtUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Looks up the members of a family (id + email + display name) from auth-service, which owns USERS —
 * notification-service has no other way to know a family member's email address. This is the one
 * synchronous cross-service call in the notification path, so it fails soft: any error is logged and
 * treated as "no recipients", so an auth-service hiccup can never make Kafka redeliver an event
 * (and re-insert its in-app notification) forever.
 */
@Component
@Slf4j(topic = "FamilyMemberDirectory")
public class FamilyMemberDirectory {

    private static final String ROLE_SERVICE = "SERVICE";
    private static final long TOKEN_TTL_MILLIS = 60_000;

    public record Member(Long userId, String email, String displayName) {
    }

    private record Envelope(List<Member> data) {
    }

    private final RestClient restClient;
    private final JwtUtil jwtUtil;

    public FamilyMemberDirectory(RestClient.Builder builder, JwtUtil jwtUtil,
                                 @Value("${app.auth-service-url}") String authServiceUrl) {
        this.restClient = builder.baseUrl(authServiceUrl).build();
        this.jwtUtil = jwtUtil;
    }

    public List<Member> listMembers(Long familyId) {
        try {
            String token = jwtUtil.generateToken("notification-service", Map.of(JwtUtil.CLAIM_ROLE, ROLE_SERVICE),
                    TOKEN_TTL_MILLIS);
            Envelope envelope = restClient.get()
                    .uri("/internal/families/{familyId}/members", familyId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Envelope.class);
            return envelope == null || envelope.data() == null ? List.of() : envelope.data();
        } catch (Exception e) {
            log.warn("Không lấy được danh sách thành viên gia đình familyId={}: {}", familyId, e.getMessage());
            return List.of();
        }
    }
}
