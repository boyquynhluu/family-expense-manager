package com.family.expensemanager.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload published by expense-service to the {@code expense-events} Kafka topic
 * (key = familyId) and consumed by notification-service. See README "Hợp đồng Kafka".
 * {@code periodMonth}/{@code limitAmount}/{@code totalSpent} are only populated for
 * {@link #BUDGET_EXCEEDED}.
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
        Instant occurredAt) {

    public static final String EXPENSE_CREATED = "EXPENSE_CREATED";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
}
