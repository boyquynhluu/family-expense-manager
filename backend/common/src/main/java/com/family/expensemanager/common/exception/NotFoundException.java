package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * @author boyquynhluu
 */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
