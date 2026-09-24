package com.family.expensemanager.common.validation;

import java.util.Currency;
import java.util.Locale;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {

    /** Null is left to {@code @NotNull}/{@code @NotBlank}, like every other Bean Validation constraint. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String code = value.trim();
        if (code.length() != 3) {
            return false;
        }
        try {
            Currency.getInstance(code.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
