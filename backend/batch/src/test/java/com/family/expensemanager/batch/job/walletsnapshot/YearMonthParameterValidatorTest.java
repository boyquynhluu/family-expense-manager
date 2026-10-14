package com.family.expensemanager.batch.job.walletsnapshot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

class YearMonthParameterValidatorTest {

    // 02:00 on 1 October in Vietnam = 19:00 on 30 September in UTC — when cron runs the job.
    private static final ZonedDateTime CRON_TIME =
            ZonedDateTime.of(2026, 10, 1, 2, 0, 0, 0, WalletSnapshotJobConfig.BUSINESS_ZONE);

    private final YearMonthParameterValidator validator = new YearMonthParameterValidator(
            Clock.fixed(CRON_TIME.toInstant(), WalletSnapshotJobConfig.BUSINESS_ZONE));

    @Test
    void monthThatJustEnded_isAccepted() {
        assertThatCode(() -> validator.validate(params("2026-09"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(params("2025-12"))).doesNotThrowAnyException();
    }

    @Test
    void currentOrFutureMonth_isRejected() {
        assertThatThrownBy(() -> validator.validate(params("2026-10")))
                .isInstanceOf(JobParametersInvalidException.class).hasMessageContaining("tháng đã kết thúc");
        assertThatThrownBy(() -> validator.validate(params("2027-01")))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    void sameInstantInUtc_wouldRejectTheMonthThatJustEnded_whichIsWhyTheClockIsInVietnamTime() {
        YearMonthParameterValidator utcValidator =
                new YearMonthParameterValidator(Clock.fixed(CRON_TIME.toInstant(), java.time.ZoneOffset.UTC));
        assertThatThrownBy(() -> utcValidator.validate(params("2026-09")))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    void missingOrMalformedParameter_isRejected() {
        assertThatThrownBy(() -> validator.validate(new JobParameters()))
                .isInstanceOf(JobParametersInvalidException.class).hasMessageContaining("Thiếu tham số");
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(JobParametersInvalidException.class);
        for (String bad : new String[] {"2026-13", "2026-9", "abc", " "}) {
            assertThatThrownBy(() -> validator.validate(params(bad)))
                    .as(bad).isInstanceOf(JobParametersInvalidException.class);
        }
    }

    private static JobParameters params(String yearMonth) {
        return new JobParametersBuilder().addString("yearMonth", yearMonth).toJobParameters();
    }
}
