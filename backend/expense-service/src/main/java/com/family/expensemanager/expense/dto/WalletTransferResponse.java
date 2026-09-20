package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.WalletTransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransferResponse(
        Long id, Long familyId, Long fromWalletId, Long toWalletId, BigDecimal amount, String note,
        LocalDateTime occurredAt, Long createdByUserId, LocalDateTime createdAt) {

    public static WalletTransferResponse from(WalletTransfer transfer) {
        return new WalletTransferResponse(
                transfer.getId(), transfer.getFamilyId(), transfer.getFromWalletId(), transfer.getToWalletId(),
                transfer.getAmount(), transfer.getNote(), transfer.getOccurredAt(), transfer.getCreatedByUserId(),
                transfer.getCreatedAt());
    }
}
