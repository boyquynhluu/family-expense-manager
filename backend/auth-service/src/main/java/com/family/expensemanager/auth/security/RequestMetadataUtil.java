package com.family.expensemanager.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Extracts the device/IP info stamped onto each new session (README "8. Không quản lý
 * được phiên đăng nhập") from the raw request — shared by every place that mints tokens
 * (login, refresh, OAuth2 success, switch-family) so they all read it the same way.
 */
public final class RequestMetadataUtil {

    private static final String CLIENT_IP_HEADER = "X-Client-Ip";

    private RequestMetadataUtil() {
    }

    public static String deviceInfo(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    /**
     * Requests only reach this service through api-gateway, which strips any inbound
     * X-Client-Ip and sets it to the trusted client IP; X-Forwarded-For is client-forgeable
     * and deliberately ignored.
     */
    public static String ipAddress(HttpServletRequest request) {
        String clientIp = request.getHeader(CLIENT_IP_HEADER);
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp.trim();
        }
        return request.getRemoteAddr();
    }
}
