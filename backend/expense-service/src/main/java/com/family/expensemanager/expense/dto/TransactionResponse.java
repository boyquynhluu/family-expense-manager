package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long walletId,
        Long categoryId,
        Long familyId,
        Long userId,
        String createdByName,
        String type,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String note,
        Boolean hasReceipt,
        LocalDateTime deletedAt,
        String deletedByName,
        Boolean isPrivate) {

    /** A non-private transaction. */
    public TransactionResponse(Long id, Long walletId, Long categoryId, Long familyId, Long userId,
                               String createdByName, String type, BigDecimal amount, LocalDateTime occurredAt,
                               String note, Boolean hasReceipt, LocalDateTime deletedAt, String deletedByName) {
        this(id, walletId, categoryId, familyId, userId, createdByName, type, amount, occurredAt, note, hasReceipt,
                deletedAt, deletedByName, false);
    }

    /**
     * Another member's private transaction as shown in the Trash: only that it exists, who deleted it and
     * when. Wallet/category/type/amount/date/note/receipt are all withheld (the client renders "***").
     */
    public static TransactionResponse masked(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), null, null, transaction.getFamilyId(), transaction.getUserId(),
                transaction.getCreatedByName(), null, null, null, null, false,
                transaction.getDeletedAt(), transaction.getDeletedByName(), true);
    }

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getCategoryId(),
                transaction.getFamilyId(),
                transaction.getUserId(),
                transaction.getCreatedByName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getNote(),
                transaction.getReceiptPath() != null,
                transaction.getDeletedAt(),
                transaction.getDeletedByName(),
                Boolean.TRUE.equals(transaction.getIsPrivate()));
    }
}
