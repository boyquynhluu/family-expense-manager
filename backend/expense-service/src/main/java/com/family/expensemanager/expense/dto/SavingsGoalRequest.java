package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.ReasonableDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author boyquynhluu
 */
public record SavingsGoalRequest(
        @NotBlank @Size(max = 100) @CleanText String name,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 16, fraction = 2) BigDecimal targetAmount,
        @ReasonableDate(maxYearsAhead = 50) LocalDate deadline,
        @NotNull Long walletId,
        Boolean archived) {
}
