package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.TransactionApproval;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record TransactionApprovalResponse(
        Long id, Long requesterUserId, String requesterName, Long walletId, Long categoryId, BigDecimal amount,
        LocalDateTime occurredAt, String note, Boolean isPrivate, String status, String decidedByName,
        LocalDateTime decidedAt, String rejectReason, Long transactionId, LocalDateTime createdAt) {

    public static TransactionApprovalResponse from(TransactionApproval a) {
        return new TransactionApprovalResponse(a.getId(), a.getRequesterUserId(), a.getRequesterName(), a.getWalletId(),
                a.getCategoryId(), a.getAmount(), a.getOccurredAt(), a.getNote(), a.getIsPrivate(), a.getStatus(),
                a.getDecidedByName(), a.getDecidedAt(), a.getRejectReason(), a.getTransactionId(), a.getCreatedAt());
    }
}
