package com.family.expensemanager.common.event;

import java.time.Instant;
import java.util.List;

/**
 * Published by auth-service to the {@code user-registered} Kafka topic (key = userId) whenever a
 * brand-new account is created, and consumed by notification-service to email every system admin.
 * {@code source} is LOCAL (registration form), GOOGLE (OAuth2 sign-up) or INVITE (accepted an invite
 * with a new account). {@code adminEmails} is resolved by auth-service at publish time because the
 * user table lives in its database.
 */
public record NewUserRegisteredEvent(
        Long userId,
        String email,
        String displayName,
        String familyName,
        String source,
        boolean emailVerified,
        List<String> adminEmails,
        Instant occurredAt) {

    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_GOOGLE = "GOOGLE";
    public static final String SOURCE_FACEBOOK = "FACEBOOK";
    public static final String SOURCE_INVITE = "INVITE";
}
