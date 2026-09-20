package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** {@code password} for accounts that have one; {@code code} (current TOTP) for provider-only accounts with 2FA. */
public record ChangeEmailRequest(@NotBlank @Email String newEmail, String password, String code) {
}
