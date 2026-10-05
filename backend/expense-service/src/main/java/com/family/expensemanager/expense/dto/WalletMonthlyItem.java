package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/**
 * One wallet's movement within a month: {@code net} is the month's surplus (positive) or
 * deficit (negative), and {@code closingBalance = openingBalance + net}. {@code adjustment} is the signed total of
 * the month's balance corrections (WALLET_ADJUSTMENTS) and {@code loanFlow} the month's borrowing/lending/repayments
 * (README B3, signed) — both part of {@code net}, never of income/expense.
 *
 * @author boyquynhluu
 */
public record WalletMonthlyItem(
        Long walletId, String walletName, String currency,
        BigDecimal openingBalance, BigDecimal income, BigDecimal expense,
        BigDecimal transferIn, BigDecimal transferOut, BigDecimal adjustment, BigDecimal loanFlow, BigDecimal net,
        BigDecimal closingBalance) {
}
