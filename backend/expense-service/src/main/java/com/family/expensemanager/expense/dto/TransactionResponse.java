package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author boyquynhluu
 */
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
        Boolean isPrivate,
        Long refundOfId,
        List<TransactionSplitPart> splits,
        List<String> tags) {

    /** Before README C4/C5/C6 (no refund link, splits or tags). */
    public TransactionResponse(Long id, Long walletId, Long categoryId, Long familyId, Long userId,
                               String createdByName, String type, BigDecimal amount, LocalDateTime occurredAt,
                               String note, Boolean hasReceipt, LocalDateTime deletedAt, String deletedByName,
                               Boolean isPrivate) {
        this(id, walletId, categoryId, familyId, userId, createdByName, type, amount, occurredAt, note, hasReceipt,
                deletedAt, deletedByName, isPrivate, null, null, null);
    }

    /** The same response with its split parts and tags attached (README C5/C6). */
    public TransactionResponse withDetails(List<TransactionSplitPart> splitParts, List<String> tagNames) {
        return new TransactionResponse(id, walletId, categoryId, familyId, userId, createdByName, type, amount,
                occurredAt, note, hasReceipt, deletedAt, deletedByName, isPrivate, refundOfId,
                splitParts == null || splitParts.isEmpty() ? null : splitParts,
                tagNames == null || tagNames.isEmpty() ? null : tagNames);
    }

    /** A non-private transaction. */
    public TransactionResponse(Long id, Long walletId, Long categoryId, Long familyId, Long userId,
                               String createdByName, String type, BigDecimal amount, LocalDateTime occurredAt,
                               String note, Boolean hasReceipt, LocalDateTime deletedAt, String deletedByName) {
        this(id, walletId, categoryId, familyId, userId, createdByName, type, amount, occurredAt, note, hasReceipt,
                deletedAt, deletedByName, false);
    }

    /**
     * Another member's private transaction as listed to everyone else: that it exists, who made it and WHEN
     * (so date filtering/sorting works for it), plus deletion info in the Trash. Wallet/category/type/amount/
     * note/receipt are withheld (the client renders "***").
     */
    public static TransactionResponse masked(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), null, null, transaction.getFamilyId(), transaction.getUserId(),
                transaction.getCreatedByName(), null, null, transaction.getOccurredAt(), null, false,
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
                Boolean.TRUE.equals(transaction.getIsPrivate()),
                transaction.getRefundOfId(),
                null,
                null);
    }
}
