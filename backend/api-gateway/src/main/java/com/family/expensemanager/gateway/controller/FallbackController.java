package com.family.expensemanager.gateway.controller;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.family.expensemanager.common.dto.ErrorResponse;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Where a route's circuit breaker forwards a call that failed or was not even attempted because the breaker
 * is open (GatewayRoutesConfig / CircuitBreakerConfiguration): a 503 in the same ErrorResponse shape as every
 * other API error, so the frontend shows its message (err.response.data.message) instead of a raw proxy error.
 * Matches every HTTP method — the forwarded request keeps the original one. Not reachable from outside:
 * nginx only sends /api/** to the gateway.
 *
 * @author boyquynhluu
 */
@RestController
@Slf4j(topic = "FallbackController")
public class FallbackController {

    private static final String MESSAGE = "Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau ít phút";
    private static final String RETRY_AFTER_SECONDS = "10";

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ErrorResponse> fallback(@PathVariable String service, HttpServletRequest request) {
        Object originalUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        String path = originalUri != null ? originalUri.toString() : request.getRequestURI();
        log.warn("Circuit breaker fallback for {} — {} {}", service, request.getMethod(), path);
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), MESSAGE, path));
    }
}
