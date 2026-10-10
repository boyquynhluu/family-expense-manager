package com.family.expensemanager.notification.config;

import java.util.function.Predicate;

import jakarta.mail.SendFailedException;

/**
 * Which mail errors count against the "smtp" circuit breaker (resilience4j.circuitbreaker.instances.smtp
 * .record-failure-predicate): every send failure EXCEPT a rejected recipient. A {@link SendFailedException}
 * anywhere in the cause chain means the SMTP server is up and refused one address (typo, closed mailbox) —
 * a handful of those must not stop email for everyone else.
 *
 * @author boyquynhluu
 */
public class SmtpFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SendFailedException) {
                return false;
            }
        }
        return true;
    }
}
