package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * README A4: the real amount of a due occurrence (e.g. this month's electricity bill) and an optional note.
 *
 * @author boyquynhluu
 */
public record ConfirmDraftRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 500) @CleanText String note) {
}
