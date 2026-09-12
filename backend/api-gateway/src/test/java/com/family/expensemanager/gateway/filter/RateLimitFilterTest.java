package com.family.expensemanager.gateway.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void allowsRequests_underTheLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void blocksWith429_onceLimitExceeded() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(loginRequest("10.0.0.2"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.2"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void tracksDifferentIps_independently() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(loginRequest("10.0.0.3"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.4"), otherIpResponse, chain);

        assertThat(otherIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimit_pathsWithoutARule() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
            request.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }
}
