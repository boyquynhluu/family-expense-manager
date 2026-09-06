package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Budget;

import java.math.BigDecimal;

public record BudgetResponse(Long id, Long familyId, Long categoryId, String periodMonth, BigDecimal limitAmount) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getId(), budget.getFamilyId(), budget.getCategoryId(),
                budget.getPeriodMonth(), budget.getLimitAmount());
    }
}
