package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/**
 * @param spentToday / spentThisMonth what the member has already spent, so the page can show "còn lại".
 *
 * @author boyquynhluu
 */
public record SpendingLimitResponse(
        Long userId, BigDecimal dailyLimit, BigDecimal monthlyLimit, BigDecimal spentToday, BigDecimal spentThisMonth) {
}
