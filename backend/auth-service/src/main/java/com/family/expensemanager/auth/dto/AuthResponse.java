package com.family.expensemanager.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType) {
}
