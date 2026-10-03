package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param ownerUserId the member who owns it, or null for a wallet shared by the whole family.
 *
 * @author boyquynhluu
 */
public record WalletResponse(
        Long id, Long familyId, String name, String currency, BigDecimal initialBalance, BigDecimal currentBalance,
        LocalDateTime deletedAt, Long ownerUserId) {

    /** A shared wallet (no owner). */
    public WalletResponse(Long id, Long familyId, String name, String currency, BigDecimal initialBalance,
                          BigDecimal currentBalance, LocalDateTime deletedAt) {
        this(id, familyId, name, currency, initialBalance, currentBalance, deletedAt, null);
    }

    /** A freshly created wallet has no transactions yet, so its current balance is just the initial one. */
    public static WalletResponse from(Wallet wallet) {
        return from(wallet, wallet.getInitialBalance());
    }

    public static WalletResponse from(Wallet wallet, BigDecimal currentBalance) {
        return new WalletResponse(
                wallet.getId(), wallet.getFamilyId(), wallet.getName(), wallet.getCurrency(),
                wallet.getInitialBalance(), currentBalance, wallet.getDeletedAt(), wallet.getOwnerUserId());
    }
}
