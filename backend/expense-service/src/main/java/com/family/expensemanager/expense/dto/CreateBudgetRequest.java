package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @author boyquynhluu
 */
public record CreateBudgetRequest(
        Long categoryId,
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String periodMonth,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal limitAmount) {
}
