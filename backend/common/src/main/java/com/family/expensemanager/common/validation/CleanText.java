package com.family.expensemanager.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A free-text value (name, note...) with no profanity and that isn't junk such as "test", "xxx" or "asdf" —
 * see {@link TextQuality}. Null/blank pass (that's {@code @NotBlank}'s job). The message depends on which rule
 * failed, so {@link #message()} is only a fallback.
 */
@Documented
@Constraint(validatedBy = CleanTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CleanText {

    /**
     * Also reject junk values ("test", "xxx", "abc"...). Turn off for very short codes where such values are
     * legitimate — e.g. a category icon like "AI" or "DX" — so only profanity is checked there.
     */
    boolean junk() default true;

    String message() default "Nội dung không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
