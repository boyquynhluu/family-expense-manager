package com.family.expensemanager.expense.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Allowed range for any amount of money that moves: thu/chi transactions, recurring rules (they create
 * transactions), CSV/Excel import and wallet transfers — not budgets or a wallet's initial balance.
 * Every family wallet uses one currency (WalletService.requireConsistentCurrency), today always VND.
 * Keep in sync with frontend utils/inputLimits.js → LIMITS.minTransactionAmount / maxTransactionAmount.
 *
 * @author boyquynhluu
 */
public final class TransactionAmounts {

    public static final BigDecimal MIN = new BigDecimal("10000");

    public static final BigDecimal MAX = new BigDecimal("5000000");

    public static final String BELOW_MIN_MESSAGE = "Số tiền giao dịch tối thiểu là 10.000đ";

    public static final String ABOVE_MAX_MESSAGE = "Số tiền giao dịch tối đa là 5.000.000đ";

    private TransactionAmounts() {
    }

    /** Why {@code amount} is not allowed, or empty when it is within [MIN, MAX] (null is left to @NotNull). */
    public static Optional<String> problem(BigDecimal amount) {
        if (amount == null) {
            return Optional.empty();
        }
        if (amount.compareTo(MIN) < 0) {
            return Optional.of(BELOW_MIN_MESSAGE);
        }
        if (amount.compareTo(MAX) > 0) {
            return Optional.of(ABOVE_MAX_MESSAGE);
        }
        return Optional.empty();
    }
}
