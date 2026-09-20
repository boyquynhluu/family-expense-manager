package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.util.List;

/** All deltas are {@code current - previous}, where "previous" is the {@code withMonth} month. */
public record CompareReportResponse(
        MonthSummary current,
        MonthSummary previous,
        BigDecimal incomeDelta,
        BigDecimal expenseDelta,
        List<CategoryCompareItem> categories) {

    public record MonthSummary(String yearMonth, BigDecimal income, BigDecimal expense) {
    }

    public record CategoryCompareItem(Long categoryId, BigDecimal current, BigDecimal previous, BigDecimal delta) {
    }
}
