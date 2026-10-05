package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.expense.domain.NameRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Allowed characters: {@link NameRules}.
 *
 * @author boyquynhluu
 */
public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) @CleanText
        @Pattern(regexp = NameRules.NAME_REGEX, message = CreateCategoryRequest.NAME_MESSAGE)
        String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
        // Short codes/emoji ("AI", "DX", "🍔") — profanity only, the junk rule would misfire on such values.
        @Size(max = 50) @CleanText(junk = false)
        @Pattern(regexp = NameRules.ICON_REGEX, message = CreateCategoryRequest.ICON_MESSAGE)
        String icon,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
        Long parentId) {

    /** A top-level category (before README C6). */
    public CreateCategoryRequest(String name, String type, String icon, String color) {
        this(name, type, icon, color, null);
    }

    public static final String NAME_MESSAGE =
            "Tên danh mục chỉ được chứa chữ, số, khoảng trắng và các ký tự " + NameRules.ALLOWED_SEPARATORS;

    public static final String ICON_MESSAGE = "Biểu tượng chỉ được là chữ, số hoặc emoji";
}
