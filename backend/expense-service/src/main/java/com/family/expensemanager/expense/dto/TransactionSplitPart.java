package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * README C5: one category part of a split transaction.
 *
 * @author boyquynhluu
 */
public record TransactionSplitPart(
        @NotNull Long categoryId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount) {
}
