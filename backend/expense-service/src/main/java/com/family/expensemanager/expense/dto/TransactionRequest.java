package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotNull Long walletId,
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "INCOME|EXPENSE") String type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        String note) {
}
