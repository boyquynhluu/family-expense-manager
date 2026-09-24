package com.family.expensemanager.common.exception;

import org.springframework.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Unexpected failure inside a service method (DB down, Kafka down, a bug...). Services let business
 * errors ({@link ApiException}, access-denied) pass through untouched and wrap everything else with
 * {@link #unexpected}, so the client always gets one generic 500 message while the real cause — with
 * its stack trace — is logged here once and never leaked into the response.
 */
@Slf4j(topic = "ServiceException")
public class ServiceException extends ApiException {

    public static final String GENERIC_MESSAGE = "Có lỗi xảy ra, vui lòng thử lại sau";

    public ServiceException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
        initCause(cause);
    }

    public static ServiceException unexpected(String operation, Exception cause) {
        return unexpected(operation, GENERIC_MESSAGE, cause);
    }

    public static ServiceException unexpected(String operation, String message, Exception cause) {
        log.error("{} - failed", operation, cause);
        return new ServiceException(message, cause);
    }
}
