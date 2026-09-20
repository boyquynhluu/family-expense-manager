package com.family.expensemanager.auth.dto;

import java.time.LocalDateTime;

import com.family.expensemanager.auth.domain.entity.RefreshToken;

public record SessionResponse(
        Long id,
        String deviceInfo,
        String ipAddress,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        boolean isCurrent) {

    public static SessionResponse from(RefreshToken token, Long currentSessionId) {
        return new SessionResponse(
                token.getId(),
                token.getDeviceInfo(),
                token.getIpAddress(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getExpiresAt(),
                token.getId().equals(currentSessionId));
    }
}
