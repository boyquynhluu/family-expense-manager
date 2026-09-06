package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateBudgetRequest(
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "\\d{4}-\\d{2}") String periodMonth,
        @NotNull @DecimalMin(value = "0.01") BigDecimal limitAmount) {
}
