package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Same filter set the Transactions page applies, shared by the paged list and the export endpoint. */
public record TransactionReportFilter(
        Long walletId, Long categoryId, String type, LocalDate fromDate, LocalDate toDate,
        String q, BigDecimal minAmount, BigDecimal maxAmount) {

    private static final char LIKE_ESCAPE = '!';
    /** Same cap as the search box's maxLength on the frontend (LIMITS.search). */
    private static final int MAX_QUERY_LENGTH = 100;

    public void validate() {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("Từ khoá tìm kiếm tối đa " + MAX_QUERY_LENGTH + " ký tự");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BadRequestException("Số tiền tối thiểu không được lớn hơn số tiền tối đa");
        }
    }

    /** Lower-cased, trimmed search text, or null when there is nothing to search for. */
    public String normalizedQuery() {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim().toLowerCase(Locale.ROOT);
    }

    /** LIKE pattern for the note column; pair with {@code ESCAPE '!'} in SQL. */
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
