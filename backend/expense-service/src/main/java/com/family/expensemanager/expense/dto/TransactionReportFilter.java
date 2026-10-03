package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Same filter set the Transactions page applies, shared by the paged list and the export endpoint.
 * Validation lives in the calling service (not here) so a business-rule violation is logged at the
 * throw site via {@code logged(log, ...)}, same as everywhere else in this codebase — a plain DTO
 * has no logger of its own.
 *
 * @author boyquynhluu
 */
public record TransactionReportFilter(
        Long walletId, Long categoryId, String type, LocalDate fromDate, LocalDate toDate,
        String q, BigDecimal minAmount, BigDecimal maxAmount) {

    private static final char LIKE_ESCAPE = '!';
    /** Same cap as the search box's maxLength on the frontend (LIMITS.search). */
    public static final int MAX_QUERY_LENGTH = 100;

    /** Lower-cased, trimmed search text, or null when there is nothing to search for. */
    public String normalizedQuery() {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim().toLowerCase(Locale.ROOT);
    }

    /** LIKE pattern for the note column; pair with {@code ESCAPE '!'} in SQL. */
    /**
     * No criterion that would reveal something about a private transaction it matched: anything but the
     * date range (wallet, category, type, note search, amount). A private transaction's DATE is shown to
     * everyone, so filtering by date leaks nothing.
     */
    public boolean filtersOnlyByDate() {
        return walletId == null && categoryId == null && type == null
                && normalizedQuery() == null && minAmount == null && maxAmount == null;
    }

    public String noteLikePattern() {
        String query = normalizedQuery();
        if (query == null) {
            return null;
        }
        StringBuilder pattern = new StringBuilder("%");
        for (char c : query.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
