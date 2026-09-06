package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateWalletRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull BigDecimal initialBalance) {
}
