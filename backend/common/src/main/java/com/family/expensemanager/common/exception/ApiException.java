package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

/** Base type for exceptions that should map directly to an HTTP status + message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
