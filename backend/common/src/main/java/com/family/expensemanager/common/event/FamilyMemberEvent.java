package com.family.expensemanager.common.event;

import java.time.Instant;

/**
 * Published by auth-service to the {@code family-member-events} Kafka topic (key = familyId)
 * when someone joins a family (invite accepted), leaves it, or is removed by the owner,
 * and consumed by notification-service to record an in-app notification for the family.
 */
public record FamilyMemberEvent(
        String eventType,
        Long familyId,
        Long memberUserId,
        String memberDisplayName,
        Instant occurredAt) {

    public static final String MEMBER_JOINED = "MEMBER_JOINED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
}
