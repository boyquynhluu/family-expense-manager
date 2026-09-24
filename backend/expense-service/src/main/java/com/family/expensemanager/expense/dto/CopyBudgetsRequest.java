package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CopyBudgetsRequest(
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String fromMonth,
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String toMonth) {
}
