package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Only required when the invited email has no account yet (a brand-new user is being
 * created); an existing account just gets a new membership and needs neither field —
 * see AuthService#acceptInvite, which validates that case manually since it can't be
 * expressed with static @NotBlank annotations here. The size limits still apply when present.
 */
public record AcceptInviteRequest(@Size(max = 100) String displayName, @Size(min = 8, max = 72) String password) {
}
