package com.family.expensemanager.expense.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One entry of a wallet's / budget's / transfer's history (README A3). {@code before}/{@code after} are the
 * entity's fields at that moment (null before a creation / after a deletion).
 *
 * @author boyquynhluu
 */
public record EntityAuditLogResponse(
        Long id, String entityType, Long entityId, String action, Long actorUserId, String actorName,
        Map<String, Object> before, Map<String, Object> after, LocalDateTime createdAt) {
}
