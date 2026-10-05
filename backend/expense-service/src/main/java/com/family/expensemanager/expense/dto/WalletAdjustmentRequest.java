package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param actualBalance the balance the wallet REALLY holds right now (counted cash, the bank app...) — the
 *                      server works out the difference from the balance in the app. May be negative
 *                      (e.g. an overdrawn card).
 *
 * @author boyquynhluu
 */
public record WalletAdjustmentRequest(
        @NotNull Long walletId,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal actualBalance,
        @Size(max = 255) @CleanText String note) {
}
