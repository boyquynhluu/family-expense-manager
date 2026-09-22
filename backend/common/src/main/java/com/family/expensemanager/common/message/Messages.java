package com.family.expensemanager.common.message;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Thin wrapper around Spring's {@link MessageSource} so services read user-facing text from a
 * properties file (one bundle per service, path set via that service's {@code spring.messages.basename})
 * instead of scattering the same String literals across the code as class constants. Always
 * resolves Vietnamese — the backend has no per-request locale switching (only the frontend's own
 * i18n does), so there is exactly one bundle per service, no locale suffix needed.
 */
@Component
@RequiredArgsConstructor
public class Messages {

    private static final Locale VI = Locale.forLanguageTag("vi");

    private final MessageSource messageSource;

    public String get(String code) {
        return messageSource.getMessage(code, null, VI);
    }

    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, VI);
    }
}
