package com.family.expensemanager.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void publicRemote_ignoresForwardedHeaders() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void privateRemote_usesRightmostForwardedEntry() {
        MockHttpServletRequest request = request("172.18.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.4");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.4");
    }

    @Test
    void privateRemote_usesRightmostAcrossMultipleHeaderLines() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        request.addHeader("X-Forwarded-For", "198.51.100.4");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.4");
    }

    @Test
    void privateRemote_skipsTrailingBlankEntries() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.4, ,");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.4");
    }

    @Test
    void privateRemote_withoutForwardedHeader_usesRemote() {
        assertThat(ClientIpResolver.resolve(request("192.168.1.20"))).isEqualTo("192.168.1.20");
    }

    @Test
    void privateRemote_withGarbageForwardedEntry_usesRemote() {
        MockHttpServletRequest request = request("172.18.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, <script>");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("172.18.0.1");
    }

    @Test
    void trustedProxyDetection_coversPrivateLoopbackLinkLocalAndUniqueLocal() {
        assertThat(ClientIpResolver.isTrustedProxy("10.1.2.3")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("172.17.0.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("192.168.0.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("127.0.0.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("169.254.1.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("fd00::1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("fe80::1")).isTrue();
    }

    @Test
    void trustedProxyDetection_rejectsPublicAndMalformed() {
        assertThat(ClientIpResolver.isTrustedProxy("8.8.8.8")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("172.32.0.1")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("2606:4700::1111")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("evil.example.com")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy(null)).isFalse();
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
