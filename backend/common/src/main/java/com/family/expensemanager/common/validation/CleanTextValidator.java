package com.family.expensemanager.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CleanTextValidator implements ConstraintValidator<CleanText, String> {

    static final String PROFANITY_MESSAGE = "Nội dung có từ ngữ không phù hợp";
    static final String JUNK_MESSAGE = "Nội dung không có ý nghĩa (ví dụ: test, xxx, asdf) — vui lòng nhập nội dung thật";

    private boolean checkJunk;

    @Override
    public void initialize(CleanText annotation) {
        this.checkJunk = annotation.junk();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return TextQuality.check(value)
                .filter(problem -> checkJunk || problem == TextQuality.Problem.PROFANITY)
                .map(problem -> {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                                    problem == TextQuality.Problem.PROFANITY ? PROFANITY_MESSAGE : JUNK_MESSAGE)
                            .addConstraintViolation();
                    return false;
                })
                .orElse(true);
    }
}
