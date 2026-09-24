package com.family.expensemanager.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** A three-letter ISO 4217 currency code (VND, USD...); case-insensitive, the service normalises to upper case. */
@Documented
@Constraint(validatedBy = IsoCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrency {

    String message() default "currency phải là mã tiền tệ ISO 4217 hợp lệ (ví dụ VND, USD)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
