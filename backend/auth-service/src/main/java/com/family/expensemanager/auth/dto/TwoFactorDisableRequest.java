package com.family.expensemanager.auth.dto;

/** Exactly one of {@code password} (accounts with a password) or {@code code} (TOTP/recovery code, accounts without one). */
public record TwoFactorDisableRequest(String password, String code) {
}
