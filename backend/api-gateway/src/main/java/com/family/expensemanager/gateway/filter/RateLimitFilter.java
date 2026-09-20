package com.family.expensemanager.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-IP fixed-window rate limiting for auth endpoints prone to brute-force/abuse
 * (login, register, forgot-password, reset-password, refresh). Runs before
 * {@link JwtGatewayFilter} so abusive traffic is rejected before any JWT parsing.
 *
 * In-memory only — fine for this project's single-gateway-instance deployment (see
 * infra/docker-compose.yml). If the gateway is ever scaled to multiple instances,
 * this would need a shared store (e.g. Redis, already used by expense-service) so
 * limits are enforced consistently across instances.
 */
@Component
@Slf4j(topic = "RateLimitFilter")
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private record Rule(String method, String path, int maxRequests, long windowMillis) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", 10, 60_000),
            new Rule("POST", "/api/auth/register", 5, 60_000),
            new Rule("POST", "/api/auth/forgot-password", 5, 60_000),
            new Rule("POST", "/api/auth/reset-password", 10, 60_000),
            new Rule("POST", "/api/auth/refresh", 30, 60_000));

    private static final class Window {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceSweep = new AtomicInteger(0);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = ClientIpResolver.resolve(request) + ":" + rule.path();
        Window window = windows.computeIfAbsent(key, k -> new Window());

        long now = System.currentTimeMillis();
        synchronized (window) {
            if (now - window.windowStart > rule.windowMillis()) {
                window.windowStart = now;
                window.count.set(0);
            }
        }
        sweepStaleWindowsOccasionally(now);

        if (window.count.incrementAndGet() > rule.maxRequests()) {
            log.warn("Rate limit vượt ngưỡng cho {} tại {}", key, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Quá nhiều yêu cầu, vui lòng thử lại sau.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Rule matchRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return RULES.stream()
                .filter(r -> r.method().equals(method) && r.path().equals(path))
                .findFirst()
                .orElse(null);
    }

    private void sweepStaleWindowsOccasionally(long now) {
        if (requestsSinceSweep.incrementAndGet() % 1000 != 0) {
            return;
        }
        long staleCutoff = now - 10 * 60_000;
        windows.entrySet().removeIf(e -> e.getValue().windowStart < staleCutoff);
    }

}
