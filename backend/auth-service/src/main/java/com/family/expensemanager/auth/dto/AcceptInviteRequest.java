package com.family.expensemanager.auth.dto;

/**
 * Only required when the invited email has no account yet (a brand-new user is being
 * created); an existing account just gets a new membership and needs neither field —
 * see AuthService#acceptInvite, which validates that case manually since it can't be
 * expressed with static @NotBlank annotations here.
 */
public record AcceptInviteRequest(String displayName, String password) {
}
