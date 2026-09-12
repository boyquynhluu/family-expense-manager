package com.family.expensemanager.auth.dto;

import com.family.expensemanager.auth.domain.entity.User;

public record UserAdminResponse(
        Long id,
        String email,
        String displayName,
        String role,
        Long familyId,
        String familyName,
        Boolean active,
        String provider,
        Boolean isSystemAdmin) {

    public static UserAdminResponse from(User user, String familyName) {
        return new UserAdminResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getFamilyId(),
                familyName, user.getActive(), user.getProvider(), Boolean.TRUE.equals(user.getIsSystemAdmin()));
    }
}
