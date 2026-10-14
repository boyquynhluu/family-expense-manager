package com.family.expensemanager.batch.job.walletsnapshot;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

/**
 * Rejects a launch before anything runs (exit code 1) unless {@code yearMonth} is a {@code yyyy-MM} month that has
 * already ended — an unfinished month cannot be snapshotted yet.
 *
 * <p>"Now" comes from the given clock, which must be in the business zone (Asia/Ho_Chi_Minh): the container runs
 * in UTC, where 02:00 on the 1st in Vietnam is still the previous day — so the month that just ended would look
 * like the current one and be refused.
 *
 * @author boyquynhluu
 */
public class YearMonthParameterValidator implements JobParametersValidator {

    static final String YEAR_MONTH = "yearMonth";

    private final Clock clock;

    public YearMonthParameterValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        String value = parameters == null ? null : parameters.getString(YEAR_MONTH);
        if (value == null || value.isBlank()) {
            throw new JobParametersInvalidException("Thiếu tham số yearMonth (yyyy-MM), ví dụ yearMonth=2026-09");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new JobParametersInvalidException("yearMonth không hợp lệ: \"" + value + "\" (cần dạng yyyy-MM)");
        }
        YearMonth current = YearMonth.now(clock);
        if (!month.isBefore(current)) {
            throw new JobParametersInvalidException(
                    "Chỉ chốt được tháng đã kết thúc: yearMonth=" + value + " (tháng hiện tại là " + current + ")");
        }
    }
}
