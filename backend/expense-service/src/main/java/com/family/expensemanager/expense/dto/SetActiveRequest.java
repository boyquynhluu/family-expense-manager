package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @author boyquynhluu
 */
public record SetActiveRequest(@NotNull Boolean active) {
}
