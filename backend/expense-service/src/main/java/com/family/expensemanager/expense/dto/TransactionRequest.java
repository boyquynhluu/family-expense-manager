package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.ReasonableDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param isPrivate only the creator may see this transaction's details (null = false). Its amount still counts
 *                  in balances/reports/budgets.
 *
 * @author boyquynhluu
 */
public record TransactionRequest(
        @NotNull Long walletId,
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "INCOME|EXPENSE") String type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotNull @ReasonableDate LocalDateTime occurredAt,
        @Size(max = 500) @CleanText String note,
        Boolean isPrivate,
        @jakarta.validation.Valid @Size(max = 10) List<TransactionSplitPart> splits,
        @Size(max = 10) List<@Size(max = 50) String> tags) {

    /** Without splits or tags (before README C5/C6). */
    public TransactionRequest(Long walletId, Long categoryId, String type, BigDecimal amount, LocalDateTime occurredAt,
                              String note, Boolean isPrivate) {
        this(walletId, categoryId, type, amount, occurredAt, note, isPrivate, null, null);
    }

    /** A normal (non-private) transaction. */
    public TransactionRequest(Long walletId, Long categoryId, String type, BigDecimal amount, LocalDateTime occurredAt,
                              String note) {
        this(walletId, categoryId, type, amount, occurredAt, note, false);
    }
}
