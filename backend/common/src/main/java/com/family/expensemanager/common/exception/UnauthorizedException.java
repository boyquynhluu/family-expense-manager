package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * @author boyquynhluu
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
