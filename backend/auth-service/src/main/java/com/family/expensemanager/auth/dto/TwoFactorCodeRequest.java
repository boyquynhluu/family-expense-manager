package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shared shape for "confirm 2FA setup" and any other request that's just a 6-digit code. */
public record TwoFactorCodeRequest(@NotBlank @Size(max = 32) String code) {
}
