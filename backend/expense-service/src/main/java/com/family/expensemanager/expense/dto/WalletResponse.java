package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Wallet;

import java.math.BigDecimal;

public record WalletResponse(
        Long id, Long familyId, String name, String currency, BigDecimal initialBalance, BigDecimal currentBalance) {

    /** A freshly created wallet has no transactions yet, so its current balance is just the initial one. */
    public static WalletResponse from(Wallet wallet) {
        return from(wallet, wallet.getInitialBalance());
    }

    public static WalletResponse from(Wallet wallet, BigDecimal currentBalance) {
        return new WalletResponse(
                wallet.getId(), wallet.getFamilyId(), wallet.getName(), wallet.getCurrency(),
                wallet.getInitialBalance(), currentBalance);
    }
}
