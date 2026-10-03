package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * @author boyquynhluu
 */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
