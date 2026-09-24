package com.family.expensemanager.common.exception;

import org.slf4j.Logger;

/**
 * Logs an exception right where a service creates it, then hands it back, so a throw site stays a single
 * expression: {@code throw logged(log, new BadRequestException("..."));}. Business errors are expected
 * outcomes, hence WARN without a stack trace; unexpected failures are logged with the stack trace by
 * {@link ServiceException#unexpected}.
 */
public final class ExceptionLogger {

    private ExceptionLogger() {
    }

    public static <T extends RuntimeException> T logged(Logger log, T exception) {
        log.warn("Ném {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
        return exception;
    }
}
