package com.family.expensemanager.expense.domain;

import java.math.BigDecimal;

/**
 * Business floor for a transaction's amount (thu/chi and recurring rules — not wallet transfers or budgets).
 * Every family wallet uses one currency (WalletService.requireConsistentCurrency), today always VND.
 * Keep in sync with frontend utils/inputLimits.js → LIMITS.minTransactionAmount.
 */
public final class TransactionAmounts {

    public static final BigDecimal MIN = new BigDecimal("10000");

    public static final String BELOW_MIN_MESSAGE = "Số tiền giao dịch tối thiểu là 10.000đ";

    private TransactionAmounts() {
    }

    public static boolean isBelowMinimum(BigDecimal amount) {
        return amount != null && amount.compareTo(MIN) < 0;
    }
}
