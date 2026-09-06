package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCategoryRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
        String icon,
        String color) {
}
