package com.family.expensemanager.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestMetadataUtilTest {

    @Test
    void ipAddress_prefersClientIpHeaderSetByGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Client-Ip", "203.0.113.7");

        assertThat(RequestMetadataUtil.ipAddress(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ipAddress_ignoresForgeableForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");

        assertThat(RequestMetadataUtil.ipAddress(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void ipAddress_fallsBackToRemoteAddr_whenClientIpHeaderBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Client-Ip", "  ");

        assertThat(RequestMetadataUtil.ipAddress(request)).isEqualTo("172.18.0.5");
    }
}
