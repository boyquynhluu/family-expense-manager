package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @author boyquynhluu
 */
public record BulkDeleteRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> ids) {
}
