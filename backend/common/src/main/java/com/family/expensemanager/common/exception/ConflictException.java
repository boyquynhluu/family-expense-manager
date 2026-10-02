package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * @author boyquynhluu
 */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
