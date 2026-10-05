package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.ReasonableDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * README C4: how much came back (positive), when, and why.
 *
 * @author boyquynhluu
 */
public record RefundRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotNull @ReasonableDate LocalDateTime occurredAt,
        @Size(max = 500) @CleanText String note) {
}
