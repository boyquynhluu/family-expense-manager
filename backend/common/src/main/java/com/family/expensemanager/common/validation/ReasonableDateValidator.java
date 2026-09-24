package com.family.expensemanager.common.validation;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class ReasonableDateValidator {

    static final int MIN_YEAR = 2000;

    private ReasonableDateValidator() {
    }

    static boolean inRange(LocalDate date, int maxYearsAhead) {
        return date == null
                || (!date.isBefore(LocalDate.of(MIN_YEAR, 1, 1)) && !date.isAfter(LocalDate.now().plusYears(maxYearsAhead)));
    }

    public static class ForLocalDate implements ConstraintValidator<ReasonableDate, LocalDate> {

        private int maxYearsAhead;

        @Override
        public void initialize(ReasonableDate annotation) {
            this.maxYearsAhead = annotation.maxYearsAhead();
        }

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return inRange(value, maxYearsAhead);
        }
    }

    public static class ForLocalDateTime implements ConstraintValidator<ReasonableDate, LocalDateTime> {

        private int maxYearsAhead;

        @Override
        public void initialize(ReasonableDate annotation) {
            this.maxYearsAhead = annotation.maxYearsAhead();
        }

        @Override
        public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
            return inRange(value == null ? null : value.toLocalDate(), maxYearsAhead);
        }
    }
}
