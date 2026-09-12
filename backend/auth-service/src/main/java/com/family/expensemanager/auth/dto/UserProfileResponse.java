package com.family.expensemanager.auth.dto;

import com.family.expensemanager.auth.domain.entity.User;

public record UserProfileResponse(
        Long id, String email, String displayName, String role, Long familyId, String provider,
        String relationship) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getFamilyId(),
                user.getProvider(), user.getRelationship());
    }
}
