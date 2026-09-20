package com.family.expensemanager.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Single source of truth for the caller's IP at the gateway. Forwarded headers are only
 * honoured when the direct peer is itself a private/loopback hop (docker network, nginx,
 * cloudflared); the RIGHTMOST X-Forwarded-For entry is the one appended by that nearest
 * trusted proxy, whereas everything to its left is client-controlled and forgeable.
 */
public final class ClientIpResolver {

    public static final String CLIENT_IP_HEADER = "X-Client-Ip";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = lastForwardedEntry(request);
        return forwarded != null ? forwarded : remote;
    }

    static boolean isTrustedProxy(String address) {
        if (address == null) {
            return false;
        }
        int scopeIndex = address.indexOf('%');
        String literal = scopeIndex >= 0 ? address.substring(0, scopeIndex) : address;
        boolean ipv4 = IPV4_LITERAL.matcher(literal).matches();
        if (!ipv4 && !(literal.indexOf(':') >= 0 && IP_LITERAL.matcher(literal).matches())) {
            return false;
        }
        try {
            // Only IPv4/IPv6 literals get here (checked above), so no DNS lookup can happen.
            InetAddress inet = InetAddress.getByName(literal);
            return inet.isLoopbackAddress()
                    || inet.isSiteLocalAddress()
                    || inet.isLinkLocalAddress()
                    || isUniqueLocalIpv6(inet);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress inet) {
        return inet instanceof Inet6Address && (inet.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static String lastForwardedEntry(HttpServletRequest request) {
        StringBuilder joined = new StringBuilder();
        for (String line : Collections.list(request.getHeaders(FORWARDED_FOR_HEADER))) {
            joined.append(line).append(',');
        }
        String[] entries = joined.toString().split(",");
        for (int i = entries.length - 1; i >= 0; i--) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) {
                continue;
            }
            return IP_LITERAL.matcher(entry).matches() ? entry : null;
        }
        return null;
    }
}
