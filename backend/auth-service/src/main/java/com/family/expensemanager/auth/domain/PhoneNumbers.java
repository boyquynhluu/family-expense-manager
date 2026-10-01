package com.family.expensemanager.auth.domain;

import java.util.regex.Pattern;

/**
 * Vietnamese mobile numbers, stored and compared in one form ({@code +84} + 9 digits) so the same number typed
 * as "0912 345 678", "84912345678" or "+84 912.345.678" maps to one account.
 */
public final class PhoneNumbers {

    /** What a normalised number must look like: +84 then a mobile prefix (3/5/7/8/9) and 8 more digits. */
    public static final String NORMALIZED_REGEX = "^\\+84[35789]\\d{8}$";

    public static final String INVALID_MESSAGE = "Số điện thoại di động không hợp lệ (VD: 0912 345 678)";

    private static final Pattern NORMALIZED = Pattern.compile(NORMALIZED_REGEX);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");

    private PhoneNumbers() {
    }

    /**
     * Strips separators and rewrites a leading {@code 0} / {@code 84} to {@code +84}. Blank becomes null; anything
     * that still isn't a valid number comes back as typed (trimmed) so validation can reject it.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        String candidate;
        if (digits.startsWith("+84")) {
            candidate = digits;
        } else if (digits.startsWith("84") && digits.length() == 11) {
            candidate = "+" + digits;
        } else if (digits.startsWith("0") && digits.length() == 10) {
            candidate = "+84" + digits.substring(1);
        } else {
            candidate = digits;
        }
        return NORMALIZED.matcher(candidate).matches() ? candidate : raw.trim();
    }

    public static boolean isValid(String normalized) {
        return normalized != null && NORMALIZED.matcher(normalized).matches();
    }

    /** A login identifier is an email if it has an '@'; anything else is treated as a phone number. */
    public static boolean looksLikeEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }
}
