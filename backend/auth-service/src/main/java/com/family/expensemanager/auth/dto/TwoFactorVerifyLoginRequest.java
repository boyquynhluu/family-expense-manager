package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyLoginRequest(@NotBlank @Size(max = 255) String challengeToken,
                                          @NotBlank @Size(max = 32) String code) {
}
