package com.family.expensemanager.expense.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record SummaryResponse(String yearMonth, BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance)
        implements Serializable {
}
