package com.family.expensemanager.auth.dto;

import com.family.expensemanager.auth.domain.PhoneNumbers;
import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * @param phone optional; normalised to +84… so it can be used to log in instead of the email.
 *
 * @author boyquynhluu
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) @CleanText String familyName,
        @NotBlank @Email @Size(max = 255) String email,
        // 72 = BCrypt only looks at the first 72 bytes; anything longer is wasted work (and a cheap DoS vector).
        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 100) @CleanText String displayName,
        @Size(max = 20) @Pattern(regexp = PhoneNumbers.NORMALIZED_REGEX, message = PhoneNumbers.INVALID_MESSAGE) String phone) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = PhoneNumbers.normalize(phone);
    }

    public RegisterRequest(String familyName, String email, String password, String displayName) {
        this(familyName, email, password, displayName, null);
    }
}
