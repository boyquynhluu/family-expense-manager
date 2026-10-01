package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/**
 * One wallet's movement within a month: {@code net} is the month's surplus (positive) or
 * deficit (negative), and {@code closingBalance = openingBalance + net}.
 */
public record WalletMonthlyItem(
        Long walletId, String walletName, String currency,
        BigDecimal openingBalance, BigDecimal income, BigDecimal expense,
        BigDecimal transferIn, BigDecimal transferOut, BigDecimal net, BigDecimal closingBalance) {
}
