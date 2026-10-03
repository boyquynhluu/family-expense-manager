package com.family.expensemanager.auth.dto;

/**
 * @author boyquynhluu
 */
public record TwoFactorSetupResponse(String secret, String otpAuthUri) {
}
