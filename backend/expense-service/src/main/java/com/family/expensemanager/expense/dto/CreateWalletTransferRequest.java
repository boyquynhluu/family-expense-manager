package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateWalletTransferRequest(
        @NotNull Long fromWalletId,
        @NotNull Long toWalletId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        @Size(max = 255) String note) {
}
