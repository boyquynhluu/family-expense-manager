package com.family.expensemanager.expense.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author boyquynhluu
 */
public record SummaryResponse(String yearMonth, BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance)
        implements Serializable {
}
