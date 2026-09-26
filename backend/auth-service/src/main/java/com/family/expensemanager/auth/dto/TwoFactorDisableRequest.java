package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.Size;

/** Exactly one of {@code password} (accounts with a password) or {@code code} (TOTP/recovery code, accounts without one). */
public record TwoFactorDisableRequest(@Size(max = 128) String password, @Size(max = 32) String code) {
}
