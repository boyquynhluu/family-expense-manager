package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/**
 * README A6: where a budget stands. {@code effectiveLimit = limitAmount + carriedOver} (the previous period's
 * unspent part when rollover is on); {@code percent} = spent / effectiveLimit.
 *
 * @author boyquynhluu
 */
public record BudgetStatusResponse(
        Long budgetId, Long categoryId, Long walletId, Long userId, String periodType, String period,
        BigDecimal limitAmount, BigDecimal carriedOver, BigDecimal effectiveLimit, BigDecimal spent, int percent) {
}
