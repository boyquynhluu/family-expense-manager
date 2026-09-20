package com.family.expensemanager.auth.dto;

import java.time.LocalDateTime;

import com.family.expensemanager.auth.domain.entity.FamilyInvite;

/** Deliberately omits the invite token — it is the secret that lets someone join the family. */
public record PendingInviteResponse(Long id, String email, LocalDateTime createdAt, LocalDateTime expiresAt) {

    public static PendingInviteResponse from(FamilyInvite invite) {
        return new PendingInviteResponse(
                invite.getId(), invite.getEmail(), invite.getCreatedAt(), invite.getExpiresAt());
    }
}
