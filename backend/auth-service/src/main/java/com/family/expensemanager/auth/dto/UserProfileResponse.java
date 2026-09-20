package com.family.expensemanager.auth.dto;

import com.family.expensemanager.auth.domain.entity.User;

public record UserProfileResponse(
        Long id, String email, String displayName, String role, Long familyId, String provider,
        String relationship, boolean totpEnabled, boolean hasPassword, boolean locked) {

    public static UserProfileResponse from(User user) {
        return from(user, user.getFamilyId(), user.getRole());
    }

    /**
     * For a specific family's member list: {@code roleInFamily} comes from that
     * family's {@code FamilyMembership} row, not {@code user.getRole()} — those two
     * can differ once a user belongs to more than one family (the latter reflects only
     * whichever family they're currently active in).
     */
    public static UserProfileResponse from(User user, Long familyId, String roleInFamily) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), roleInFamily, familyId,
                user.getProvider(), user.getRelationship(), Boolean.TRUE.equals(user.getTotpEnabled()),
                user.getPasswordHash() != null, Boolean.TRUE.equals(user.getLocked()));
    }
}
