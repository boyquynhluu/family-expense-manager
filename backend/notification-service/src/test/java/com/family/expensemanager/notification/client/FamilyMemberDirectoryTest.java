package com.family.expensemanager.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.family.expensemanager.common.security.JwtUtil;
import com.sun.net.httpserver.HttpServer;

/**
 * Calls a stub auth-service over real HTTP (JDK HttpServer), so the RestClient wiring — base URL, timeouts,
 * service token, JSON envelope — is exercised for real. The retry/circuit-breaker fallback is AOP (Resilience4j
 * annotations) and only applies through the Spring proxy, so it is not part of this unit test.
 */
class FamilyMemberDirectoryTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-0123456789";

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    @BeforeEach
    void startStubAuthService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/families/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] body = """
                    {"success":true,"data":[
                      {"userId":10,"email":"me@b.com","displayName":"Mẹ","role":"OWNER"},
                      {"userId":11,"email":"bo@b.com","displayName":"Bố","role":"MEMBER"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void listMembers_callsTheInternalEndpointWithAServiceToken_andReturnsTheMembers() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        FamilyMemberDirectory directory = new FamilyMemberDirectory(RestClient.builder(), new JwtUtil(SECRET),
                baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));

        List<FamilyMemberDirectory.Member> members = directory.listMembers(42L);

        assertThat(path.get()).isEqualTo("/internal/families/42/members");
        assertThat(authorization.get()).startsWith("Bearer ");
        assertThat(members).extracting(FamilyMemberDirectory.Member::email).containsExactly("me@b.com", "bo@b.com");
        assertThat(members.get(0).role()).isEqualTo("OWNER");
    }
}
