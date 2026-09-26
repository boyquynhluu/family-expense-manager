package com.family.expensemanager.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A string whose UTF-8 encoding is at most {@link #value()} bytes. Needed for passwords: BCrypt only ever uses
 * the first 72 BYTES and silently ignores the rest, while {@code @Size} counts characters — a 72-character
 * Vietnamese password ("ư", "ệ"... are 2-3 bytes each) can be well over 72 bytes, so two different passwords
 * sharing that prefix would both be accepted.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Mật khẩu quá dài (tối đa {value} byte — ký tự có dấu chiếm 2-3 byte)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
