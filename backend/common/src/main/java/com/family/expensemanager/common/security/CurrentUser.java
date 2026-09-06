package com.family.expensemanager.common.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated user's id/familyId/role from the JWT claims that
 * {@link JwtAuthenticationFilter} attached to the current request's SecurityContext.
 * Controllers must scope every query through this — never trust a client-supplied
 * familyId — so one family can never read or write another family's data.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long userId() {
        return Long.valueOf(authentication().getName());
    }

    public static Long familyId() {
        return claims().get(JwtUtil.CLAIM_FAMILY_ID, Long.class);
    }

    public static String role() {
        return claims().get(JwtUtil.CLAIM_ROLE, String.class);
    }

    private static Claims claims() {
        return (Claims) authentication().getDetails();
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return authentication;
    }
}
