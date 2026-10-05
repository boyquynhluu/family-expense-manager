package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * README A5: null = no cap for that period. Both null removes the member's limits.
 *
 * @author boyquynhluu
 */
public record SpendingLimitRequest(
        @Positive @Digits(integer = 16, fraction = 2) BigDecimal dailyLimit,
        @Positive @Digits(integer = 16, fraction = 2) BigDecimal monthlyLimit) {
}
