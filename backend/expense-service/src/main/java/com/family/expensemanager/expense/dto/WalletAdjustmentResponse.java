package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.WalletAdjustment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record WalletAdjustmentResponse(
        Long id, Long walletId, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String note,
        LocalDateTime occurredAt, Long createdByUserId, String createdByName) {

    public static WalletAdjustmentResponse from(WalletAdjustment a) {
        return new WalletAdjustmentResponse(
                a.getId(), a.getWalletId(), a.getAmount(), a.getBalanceBefore(), a.getBalanceAfter(), a.getNote(),
                a.getOccurredAt(), a.getCreatedByUserId(), a.getCreatedByName());
    }
}
