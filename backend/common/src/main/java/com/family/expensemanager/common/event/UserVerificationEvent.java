package com.family.expensemanager.common.event;

import java.time.Instant;

/**
 * Published by auth-service to the {@code user-verification} Kafka topic (key = userId)
 * right after a new user registers with {@code active = false}, and consumed by
 * notification-service to send the verification email.
 */
public record UserVerificationEvent(
        Long userId,
        String email,
        String displayName,
        String verificationToken,
        Instant occurredAt) {
}
