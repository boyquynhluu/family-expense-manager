package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
        @Size(max = 50) String icon,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {
}
