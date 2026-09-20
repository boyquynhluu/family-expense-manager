package com.family.expensemanager.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpFilterTest {

    private final ClientIpFilter filter = new ClientIpFilter();

    @Test
    void replacesClientSuppliedClientIpHeader_withTrustedValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/expenses/wallets");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Client-Ip", "6.6.6.6");
        request.addHeader("x-client-ip", "7.7.7.7");
        request.addHeader("User-Agent", "test");

        HttpServletRequest forwarded = run(request);

        assertThat(forwarded.getHeader("X-Client-Ip")).isEqualTo("203.0.113.9");
        assertThat(Collections.list(forwarded.getHeaders("x-client-ip"))).containsExactly("203.0.113.9");
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .filteredOn(name -> name.equalsIgnoreCase("X-Client-Ip"))
                .hasSize(1);
        assertThat(forwarded.getHeader("User-Agent")).isEqualTo("test");
    }

    @Test
    void setsRightmostForwardedEntry_whenRemoteIsPrivateProxy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRemoteAddr("172.18.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.4");

        assertThat(run(request).getHeader("X-Client-Ip")).isEqualTo("198.51.100.4");
    }

    private HttpServletRequest run(MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set((HttpServletRequest) req));
        return seen.get();
    }
}
