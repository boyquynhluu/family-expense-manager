package com.family.expensemanager.common.dto;

import java.time.Instant;

/**
 * Uniform error envelope returned by {@code GlobalExceptionHandler}.
 *
 * @author boyquynhluu
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
