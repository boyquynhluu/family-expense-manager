package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.ReasonableDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRecurringTransactionRequest(
        @NotNull Long walletId,
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "INCOME|EXPENSE") String type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 500) String note,
        @Pattern(regexp = "MONTHLY|WEEKLY|YEARLY") String frequency,
        @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(7) Integer dayOfWeek,
        @Min(1) @Max(12) Integer monthOfYear,
        @NotNull @ReasonableDate(maxYearsAhead = 5) LocalDate startDate,
        @ReasonableDate(maxYearsAhead = 50) LocalDate endDate) {
}
