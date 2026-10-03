package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.TransferRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param fromWalletName null when that wallet has since been deleted
 * @param canDecide      the viewer is the wallet owner who may approve/reject it, and it is still pending
 *
 * @author boyquynhluu
 */
public record TransferRequestResponse(
        Long id, Long requesterUserId, String requesterName, Long approverUserId,
        Long fromWalletId, String fromWalletName, Long toWalletId, String toWalletName,
        BigDecimal amount, String note, String status,
        String decidedByName, LocalDateTime decidedAt, Long transferId, LocalDateTime createdAt,
        boolean canDecide) {

    public static TransferRequestResponse from(TransferRequest r, String fromWalletName, String toWalletName,
                                               Long viewerUserId) {
        boolean canDecide = TransferRequest.PENDING.equals(r.getStatus())
                && r.getApproverUserId().equals(viewerUserId);
        return new TransferRequestResponse(
                r.getId(), r.getRequesterUserId(), r.getRequesterName(), r.getApproverUserId(),
                r.getFromWalletId(), fromWalletName, r.getToWalletId(), toWalletName,
                r.getAmount(), r.getNote(), r.getStatus(),
                r.getDecidedByName(), r.getDecidedAt(), r.getTransferId(), r.getCreatedAt(), canDecide);
    }
}
