package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** {@code password} for accounts that have one; {@code code} (current TOTP) for provider-only accounts with 2FA. */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 255) String newEmail,
        @Size(max = 128) String password,
        @Size(max = 32) String code) {

    public ChangeEmailRequest {
        newEmail = newEmail == null ? null : newEmail.trim().toLowerCase(Locale.ROOT);
    }
}
