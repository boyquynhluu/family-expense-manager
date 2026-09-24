package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.IsoCurrency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateWalletRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @IsoCurrency String currency,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal initialBalance) {
}
