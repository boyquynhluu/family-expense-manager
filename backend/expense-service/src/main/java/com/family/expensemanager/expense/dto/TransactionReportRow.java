package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One flattened, display-ready row of a transaction report — wallet/category ids already resolved to names. */
public record TransactionReportRow(
        LocalDateTime occurredAt,
        String walletName,
        String categoryName,
        String typeLabel,
        BigDecimal amount,
        String note) {
}
