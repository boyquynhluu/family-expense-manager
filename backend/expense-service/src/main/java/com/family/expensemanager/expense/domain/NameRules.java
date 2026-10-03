package com.family.expensemanager.expense.domain;

/**
 * Allowed characters for user-typed names and icons (wallets, categories). Mirrored in frontend
 * utils/namePatterns.js — keep in sync.
 *
 * @author boyquynhluu
 */
public final class NameRules {

    /**
     * Letters (any language, with their accents), digits, spaces and a few separators people actually use in a
     * name ("Ăn uống & cà phê", "Điện/nước", "Ví (chung)") — and at least one letter.
     */
    public static final String NAME_REGEX = "^(?=.*\\p{L})[\\p{L}\\p{M}\\p{N} .,&()/'+_-]+$";

    /**
     * Letters/digits (a short code like "AI") or emoji — incl. joiners, variation selectors, skin tones, keycaps
     * and flags, so "👨‍👩‍👧", "❤️", "👍🏽", "1️⃣" and "🇻🇳" all pass. Empty means no icon.
     */
    public static final String ICON_REGEX = "^[\\p{L}\\p{M}\\p{N}\\p{IsExtended_Pictographic}"
            + "\\x{200D}\\x{FE0F}\\x{20E3}\\x{1F3FB}-\\x{1F3FF}\\x{1F1E6}-\\x{1F1FF} _-]*$";

    public static final String ALLOWED_SEPARATORS = "- _ . , & ( ) / ' +";

    private NameRules() {
    }
}
