package com.family.expensemanager.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.family.expensemanager.common.dto.ErrorResponse;

import jakarta.servlet.RequestDispatcher;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void forwardedCall_gets503WithTheOriginalPathAndARetryAfter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fallback/expense-service");
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/api/expenses/transactions");

        ResponseEntity<ErrorResponse> response = controller.fallback("expense-service", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("10");
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(503);
        assertThat(body.path()).isEqualTo("/api/expenses/transactions");
        assertThat(body.message()).isNotBlank();
    }

    @Test
    void withoutForwardAttribute_reportsTheRequestUri() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fallback/auth-service");

        ResponseEntity<ErrorResponse> response = controller.fallback("auth-service", request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/fallback/auth-service");
    }
}
