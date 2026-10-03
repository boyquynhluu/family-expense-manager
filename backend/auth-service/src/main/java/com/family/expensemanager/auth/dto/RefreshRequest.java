package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @author boyquynhluu
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
