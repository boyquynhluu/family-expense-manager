package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/**
 * README C6: income and expense of one tag over a date range.
 *
 * @author boyquynhluu
 */
public record TagReportItem(Long tagId, String name, BigDecimal income, BigDecimal expense) {
}
