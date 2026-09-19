package com.family.expensemanager.auth.dto;

/**
 * What {@code POST /api/auth/login} actually returns now that 2FA exists: either the
 * real tokens ({@code requiresTwoFactor=false}), or a short-lived challenge the client
 * must resolve via {@code POST /api/auth/2fa/verify-login} before it gets any tokens.
 */
public record LoginResponse(boolean requiresTwoFactor, String twoFactorToken, AuthResponse tokens) {

    public static LoginResponse ofTokens(AuthResponse tokens) {
        return new LoginResponse(false, null, tokens);
    }

    public static LoginResponse ofChallenge(String twoFactorToken) {
        return new LoginResponse(true, twoFactorToken, null);
    }
}
