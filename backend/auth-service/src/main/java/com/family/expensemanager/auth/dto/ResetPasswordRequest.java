package com.family.expensemanager.auth.dto;

import com.family.expensemanager.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 255) String token,
        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String newPassword) {
}
