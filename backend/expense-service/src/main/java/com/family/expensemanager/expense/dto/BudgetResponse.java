package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Budget;

import java.math.BigDecimal;

/**
 * @author boyquynhluu
 */
public record BudgetResponse(Long id, Long familyId, Long categoryId, String periodMonth, BigDecimal limitAmount,
                             Long walletId, Long userId, String periodType, Boolean rollover) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getId(), budget.getFamilyId(), budget.getCategoryId(),
                budget.getPeriodMonth(), budget.getLimitAmount(), budget.getWalletId(), budget.getUserId(),
                budget.getPeriodType(), budget.getRollover());
    }
}
