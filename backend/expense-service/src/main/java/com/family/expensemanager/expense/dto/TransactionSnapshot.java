package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.family.expensemanager.expense.domain.entity.Transaction;

/**
 * The user-visible state of a transaction at one point in time, as stored in TRANSACTION_AUDIT_LOGS
 * before_json / after_json. Deliberately not the entity itself: the history should keep describing what
 * the user saw even if internal columns (version, receipt path...) change shape later.
 *
 * @author boyquynhluu
 */
public record TransactionSnapshot(
        Long walletId,
        Long categoryId,
        String type,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String note,
        boolean hasReceipt) {

    public static TransactionSnapshot of(Transaction transaction) {
        return new TransactionSnapshot(
                transaction.getWalletId(),
                transaction.getCategoryId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getNote(),
                transaction.getReceiptPath() != null);
    }
}
