package com.family.expensemanager.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload published by expense-service to the {@code expense-events} Kafka topic
 * (key = familyId) and consumed by notification-service. See README "Hợp đồng Kafka".
 * {@code periodMonth}/{@code limitAmount}/{@code totalSpent}/{@code categoryName}/
 * {@code userEmail}/{@code userDisplayName} are only populated for
 * {@link #BUDGET_EXCEEDED} and {@link #BUDGET_WARNING} (both with a null {@code categoryId}
 * and {@code categoryName} "Tổng chi tiêu" for the family-wide budget) — notification-service has no direct access to expense-
 * service's categories or auth-service's users, so expense-service (which has both,
 * the latter via the caller's JWT claims) fills them in at publish time rather than
 * notification-service making a synchronous cross-service call to look them up.
 */
public record ExpenseEvent(
        String eventType,
        Long familyId,
        Long userId,
        Long transactionId,
        Long categoryId,
        BigDecimal amount,
        String periodMonth,
        BigDecimal limitAmount,
        BigDecimal totalSpent,
        String categoryName,
        String userEmail,
        String userDisplayName,
        Instant occurredAt) {

    public static final String EXPENSE_CREATED = "EXPENSE_CREATED";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    public static final String BUDGET_WARNING = "BUDGET_WARNING";
}
