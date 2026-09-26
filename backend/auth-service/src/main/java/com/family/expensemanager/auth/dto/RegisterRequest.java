package com.family.expensemanager.auth.dto;

import com.family.expensemanager.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String familyName,
        @NotBlank @Email @Size(max = 255) String email,
        // 72 = BCrypt only looks at the first 72 bytes; anything longer is wasted work (and a cheap DoS vector).
        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 100) String displayName) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
