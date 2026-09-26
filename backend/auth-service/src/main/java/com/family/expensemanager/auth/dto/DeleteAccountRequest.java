package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.Size;

/** {@code password} for accounts that have one; {@code code} (current TOTP) for provider-only accounts with 2FA. */
public record DeleteAccountRequest(@Size(max = 128) String password, @Size(max = 32) String code) {
}
