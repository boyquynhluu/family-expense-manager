package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearReportResponse(
        int year,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        List<MonthTotal> months) {

    public record MonthTotal(String yearMonth, BigDecimal income, BigDecimal expense) {
    }
}
