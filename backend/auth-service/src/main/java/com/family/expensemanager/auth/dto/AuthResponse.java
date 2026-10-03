package com.family.expensemanager.auth.dto;

/**
 * @author boyquynhluu
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType) {
}
