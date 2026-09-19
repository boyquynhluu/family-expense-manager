package com.family.expensemanager.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Extracts the device/IP info stamped onto each new session (README "8. Không quản lý
 * được phiên đăng nhập") from the raw request — shared by every place that mints tokens
 * (login, refresh, OAuth2 success, switch-family) so they all read it the same way.
 */
public final class RequestMetadataUtil {

    private RequestMetadataUtil() {
    }

    public static String deviceInfo(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    /**
     * Requests arrive via api-gateway, so the direct socket address is always the
     * gateway's own IP — the real client IP (if forwarded) is in X-Forwarded-For.
     */
    public static String ipAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
