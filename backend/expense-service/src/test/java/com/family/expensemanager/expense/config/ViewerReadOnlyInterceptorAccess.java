package com.family.expensemanager.expense.config;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Test helper: runs {@link ViewerReadOnlyInterceptor} for one method and authority (README A5).
 */
public final class ViewerReadOnlyInterceptorAccess {

    private ViewerReadOnlyInterceptorAccess() {
    }

    public static boolean allows(String method, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "7", null, List.of(new SimpleGrantedAuthority(authority))));
        try {
            return new ViewerReadOnlyInterceptor().preHandle(
                    new MockHttpServletRequest(method, "/api/expenses/transactions"), new MockHttpServletResponse(), null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
