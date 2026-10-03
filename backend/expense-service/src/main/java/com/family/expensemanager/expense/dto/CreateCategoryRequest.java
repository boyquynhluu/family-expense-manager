package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Allowed characters are mirrored in frontend pages/Categories.jsx (NAME_PATTERN / ICON_PATTERN) — keep in sync.
 *
 * @author boyquynhluu
 */
public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) @CleanText
        @Pattern(regexp = CreateCategoryRequest.NAME_REGEX, message = CreateCategoryRequest.NAME_MESSAGE)
        String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
        // Short codes/emoji ("AI", "DX", "🍔") — profanity only, the junk rule would misfire on such values.
        @Size(max = 50) @CleanText(junk = false)
        @Pattern(regexp = CreateCategoryRequest.ICON_REGEX, message = CreateCategoryRequest.ICON_MESSAGE)
        String icon,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {

    /**
     * Letters (any language, with their accents), digits, spaces and a few separators people actually use in a
     * category name ("Ăn uống & cà phê", "Điện/nước", "Học phí (con)") — and at least one letter.
     */
    public static final String NAME_REGEX = "^(?=.*\\p{L})[\\p{L}\\p{M}\\p{N} .,&()/'+_-]+$";

    public static final String NAME_MESSAGE =
            "Tên danh mục chỉ được chứa chữ, số, khoảng trắng và các ký tự - _ . , & ( ) / ' +";

    /**
     * Letters/digits (a short code like "AI") or emoji — incl. joiners, variation selectors, skin tones, keycaps
     * and flags, so "👨‍👩‍👧", "❤️", "👍🏽", "1️⃣" and "🇻🇳" all pass. Empty means no icon.
     */
    public static final String ICON_REGEX = "^[\\p{L}\\p{M}\\p{N}\\p{IsExtended_Pictographic}"
            + "\\x{200D}\\x{FE0F}\\x{20E3}\\x{1F3FB}-\\x{1F3FF}\\x{1F1E6}-\\x{1F1FF} _-]*$";

    public static final String ICON_MESSAGE = "Biểu tượng chỉ được là chữ, số hoặc emoji";
}
