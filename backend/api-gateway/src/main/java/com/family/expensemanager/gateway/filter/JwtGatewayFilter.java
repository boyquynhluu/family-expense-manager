package com.family.expensemanager.gateway.filter;

import com.family.expensemanager.common.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.gateway.server.mvc.filter.FormFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Pre-validates JWTs at the gateway so bad requests fail fast, without being the sole
 * source of truth — each downstream service re-verifies independently (see README
 * "Bảo mật"). The raw {@code Authorization} header is left untouched and forwarded as-is.
 *
 * Spring Cloud Gateway Server MVC (servlet-based) has no GlobalFilter equivalent
 * (see spring-cloud/spring-cloud-gateway#3239 — closed wontfix), so this is a plain
 * servlet {@link jakarta.servlet.Filter} instead, ordered to run before the gateway's
 * own {@link FormFilter}.
 */
@Component
public class JwtGatewayFilter extends OncePerRequestFilter implements Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh");

    private final JwtUtil jwtUtil;

    public JwtGatewayFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public int getOrder() {
        return FormFilter.FORM_FILTER_ORDER - 1;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        try {
            jwtUtil.parseClaims(header.substring(7));
        } catch (Exception e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.endsWith("/v3/api-docs")
                || path.contains("/v3/api-docs/")
                || path.startsWith("/swagger-ui")
                || path.equals("/actuator/health");
    }
}
