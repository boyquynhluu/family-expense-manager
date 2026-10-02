package com.family.expensemanager.auth.dto;

import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record FamilyAdminResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        long memberCount,
        String ownerEmail,
        String ownerDisplayName) {
}
