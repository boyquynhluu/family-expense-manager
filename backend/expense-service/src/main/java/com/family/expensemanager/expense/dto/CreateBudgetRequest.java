package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * @param categoryId null = every expense category (the "overall" budget); a parent category covers its children.
 * @param walletId   README A6: only spending from this wallet (null = every wallet).
 * @param userId     README A6: only this member's spending (null = the whole family).
 * @param periodType README A6: MONTH (periodMonth "yyyy-MM", default) or YEAR (periodMonth "yyyy").
 * @param rollover   README A6: the unspent part of the previous period's budget (same scope) is added to this one.
 *
 * @author boyquynhluu
 */
public record CreateBudgetRequest(
        Long categoryId,
        @NotNull @Pattern(regexp = "\\d{4}(-(0[1-9]|1[0-2]))?") String periodMonth,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal limitAmount,
        Long walletId,
        Long userId,
        @Pattern(regexp = "MONTH|YEAR") String periodType,
        Boolean rollover) {

    /** A plain monthly budget (before README A6). */
    public CreateBudgetRequest(Long categoryId, String periodMonth, BigDecimal limitAmount) {
        this(categoryId, periodMonth, limitAmount, null, null, null, null);
    }
}
