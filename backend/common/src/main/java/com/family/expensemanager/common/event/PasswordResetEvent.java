package com.family.expensemanager.common.event;

import java.time.Instant;

/**
 * Published by auth-service to the {@code password-reset} Kafka topic (key = userId)
 * when a user requests a password reset, and consumed by notification-service to send
 * the reset email.
 */
public record PasswordResetEvent(
        Long userId,
        String email,
        String displayName,
        String resetToken,
        Instant occurredAt) {
}
