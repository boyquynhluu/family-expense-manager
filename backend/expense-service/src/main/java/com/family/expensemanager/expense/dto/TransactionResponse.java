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
        String type,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String note) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getCategoryId(),
                transaction.getFamilyId(),
                transaction.getUserId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getNote());
    }
}
