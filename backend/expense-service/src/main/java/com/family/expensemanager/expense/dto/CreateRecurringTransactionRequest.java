package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRecurringTransactionRequest(
        @NotNull Long walletId,
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "INCOME|EXPENSE") String type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String note,
        @Pattern(regexp = "MONTHLY|WEEKLY|YEARLY") String frequency,
        @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(7) Integer dayOfWeek,
        @Min(1) @Max(12) Integer monthOfYear,
        @NotNull LocalDate startDate,
        LocalDate endDate) {
}
