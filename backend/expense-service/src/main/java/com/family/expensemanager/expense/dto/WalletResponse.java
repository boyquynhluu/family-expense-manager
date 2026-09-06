package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Wallet;

import java.math.BigDecimal;

public record WalletResponse(Long id, Long familyId, String name, String currency, BigDecimal initialBalance) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getFamilyId(), wallet.getName(), wallet.getCurrency(), wallet.getInitialBalance());
    }
}
