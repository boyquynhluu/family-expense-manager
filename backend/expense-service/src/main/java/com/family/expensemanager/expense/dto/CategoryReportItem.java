package com.family.expensemanager.expense.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record CategoryReportItem(Long categoryId, String categoryName, BigDecimal total) implements Serializable {
}
