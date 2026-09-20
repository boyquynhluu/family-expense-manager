package com.family.expensemanager.auth.dto;

/** {@code password} for accounts that have one; {@code code} (current TOTP) for provider-only accounts with 2FA. */
public record DeleteAccountRequest(String password, String code) {
}
