package com.family.expensemanager.expense.dto;

import java.time.LocalDateTime;

/**
 * One entry of {@code GET /transactions/{id}/history}; {@code before}/{@code after} are null where they don't apply.
 *
 * @author boyquynhluu
 */
public record TransactionAuditLogResponse(
        Long id,
        String action,
        Long actorUserId,
        String actorName,
        TransactionSnapshot before,
        TransactionSnapshot after,
        LocalDateTime createdAt) {
}
