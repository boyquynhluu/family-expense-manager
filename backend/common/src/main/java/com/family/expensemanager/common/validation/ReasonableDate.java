package com.family.expensemanager.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A date/date-time that isn't absurd: not before {@value ReasonableDateValidator#MIN_YEAR}-01-01 and not more
 * than {@link #maxYearsAhead()} years after today. Works on {@code LocalDate} and {@code LocalDateTime}.
 */
@Documented
@Constraint(validatedBy = {ReasonableDateValidator.ForLocalDate.class, ReasonableDateValidator.ForLocalDateTime.class})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReasonableDate {

    String message() default "Ngày không hợp lệ (phải từ năm 2000 và không quá xa trong tương lai)";

    int maxYearsAhead() default 1;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
