package com.family.expensemanager.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Stamps the trusted client IP (see {@link ClientIpResolver}) onto {@code X-Client-Ip} for
 * downstream services, discarding any value the caller sent. Wrapping the servlet request
 * is enough because Gateway Server MVC builds the proxied request's headers from it.
 */
@Component
public class ClientIpFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(new ClientIpRequest(request, ClientIpResolver.resolve(request)), response);
    }

    private static final class ClientIpRequest extends HttpServletRequestWrapper {

        private final String clientIp;

        ClientIpRequest(HttpServletRequest request, String clientIp) {
            super(request);
            this.clientIp = clientIp;
        }

        @Override
        public String getHeader(String name) {
            if (ClientIpResolver.CLIENT_IP_HEADER.equalsIgnoreCase(name)) {
                return clientIp;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (ClientIpResolver.CLIENT_IP_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(clientIp));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            for (String name : Collections.list(super.getHeaderNames())) {
                if (!ClientIpResolver.CLIENT_IP_HEADER.equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            names.add(ClientIpResolver.CLIENT_IP_HEADER);
            return Collections.enumeration(names);
        }
    }
}
