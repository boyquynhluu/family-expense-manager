package com.family.expensemanager.auth.dto;

public record TwoFactorSetupResponse(String secret, String otpAuthUri) {
}
