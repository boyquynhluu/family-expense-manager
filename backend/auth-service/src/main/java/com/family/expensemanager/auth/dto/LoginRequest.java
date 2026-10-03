package com.family.expensemanager.auth.dto;

import com.family.expensemanager.auth.domain.PhoneNumbers;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * @param identifier the account's email or its phone number (anything without an '@' is read as a phone number).
 *                   Still accepted under the old JSON name {@code email}.
 *
 * @author boyquynhluu
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) @JsonAlias("email") String identifier,
        @NotBlank @Size(max = 128) String password) {

    /**
     * Emails are trimmed and lower-cased, phone numbers normalised to +84…, on the way in — so the login-lockout
     * counter and every lookup use one key however the user typed it.
     */
    public LoginRequest {
        if (identifier != null) {
            identifier = PhoneNumbers.looksLikeEmail(identifier)
                    ? identifier.trim().toLowerCase(Locale.ROOT)
                    : PhoneNumbers.normalize(identifier);
        }
    }
}
