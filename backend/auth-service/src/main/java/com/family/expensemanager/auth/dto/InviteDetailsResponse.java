package com.family.expensemanager.auth.dto;

/**
 * {@code isExistingAccount} tells the accept-invite page whether to show the
 * displayName/password fields at all — an existing account just adds a membership to
 * the new family and needs neither (see AuthService#acceptInvite).
 */
public record InviteDetailsResponse(String email, String familyName, boolean isExistingAccount) {
}
