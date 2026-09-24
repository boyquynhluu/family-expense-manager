package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateBudgetRequest(
        Long categoryId,
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String periodMonth,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal limitAmount) {
}
