package com.family.expensemanager.common.event;

import java.time.Instant;

/**
 * Published by auth-service to the {@code family-invite} Kafka topic (key = familyId)
 * when a family owner invites someone by email, and consumed by notification-service
 * to send the invite email.
 */
public record FamilyInviteEvent(
        Long familyId,
        String familyName,
        String email,
        String inviterDisplayName,
        String token,
        Instant occurredAt) {
}
