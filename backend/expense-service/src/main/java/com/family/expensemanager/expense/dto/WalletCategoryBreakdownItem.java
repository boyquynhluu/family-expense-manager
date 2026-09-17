package com.family.expensemanager.expense.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record WalletCategoryBreakdownItem(Long walletId, Long categoryId, BigDecimal total) implements Serializable {
}
