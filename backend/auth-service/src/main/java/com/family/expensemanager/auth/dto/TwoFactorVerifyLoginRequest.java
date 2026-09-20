package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyLoginRequest(@NotBlank String challengeToken, @NotBlank String code) {
}
