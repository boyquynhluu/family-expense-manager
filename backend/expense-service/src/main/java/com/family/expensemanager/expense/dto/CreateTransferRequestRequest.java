package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * "Xin chuyển tiền": ask the owner of {@code fromWalletId} (another member's private wallet) to move
 * {@code amount} into {@code toWalletId} (the requester's own private wallet). The 10.000đ–5.000.000đ range is
 * checked by the service (TransactionAmounts), like every other amount of money that moves.
 *
 * @author boyquynhluu
 */
public record CreateTransferRequestRequest(
        @NotNull Long fromWalletId,
        @NotNull Long toWalletId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 255) @CleanText String note) {
}
