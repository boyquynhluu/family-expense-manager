package com.family.expensemanager.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.seasar.doma.jdbc.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.family.expensemanager.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared error-response mapping so every service returns the same {@link ErrorResponse} shape.
 *
 * @author boyquynhluu
 */
@RestControllerAdvice
@Slf4j(topic = "GlobalExceptionHandler")
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        // Business exceptions are already logged with more detail (which service/method threw it) right
        // where they're created, via ExceptionLogger.logged(...) or ServiceException.unexpected(...) —
        // this is just the request-path context, so DEBUG is enough to avoid a duplicate WARN per request.
        log.debug("ApiException tại {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * A versioned entity (see {@code @Version} on Budget/Transaction) was updated by someone else between
     * this request's read and write — Doma throws this when its generated UPDATE affects 0 rows because
     * the WHERE version = ? no longer matches. 409 (not 500): the client should reload and retry, not
     * treat it as an unexpected server error.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex, HttpServletRequest request) {
        log.warn("Xung đột cập nhật (dữ liệu đã đổi) tại {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Dữ liệu đã được người khác thay đổi, vui lòng tải lại trang", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Truy cập bị từ chối tại {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Lỗi validation tại {}: {}", request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // IllegalArgumentException already matches this (it's an Exception too), so a separate handler for
    // it would be dead code — and ex.getMessage() isn't returned here because an unexpected exception can
    // carry internal detail (table/column names, SQL) that shouldn't reach the client; full detail still
    // goes to the log via `ex`.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không xác định tại {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
