package com.family.expensemanager.expense.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author boyquynhluu
 */
public record WalletCategoryBreakdownItem(Long walletId, Long categoryId, BigDecimal total) implements Serializable {
}
