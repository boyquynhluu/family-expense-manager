package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) @CleanText String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
        // Short codes/emoji ("AI", "DX", "🍔") — profanity only, the junk rule would misfire on such values.
        @Size(max = 50) @CleanText(junk = false) String icon,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {
}
