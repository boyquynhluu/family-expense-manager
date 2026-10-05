package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param saved   the linked wallet's current balance (never below 0 for the progress).
 * @param percent saved / target, capped at 100.
 *
 * @author boyquynhluu
 */
public record SavingsGoalResponse(
        Long id, String name, BigDecimal targetAmount, LocalDate deadline, Long walletId, BigDecimal saved,
        int percent, Boolean archived, Long createdByUserId, String createdByName) {
}
