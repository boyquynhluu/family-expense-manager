package com.family.expensemanager.expense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Enables {@code @Scheduled} (used by {@code RecurringTransactionScheduler}) and
 * exposes the system {@link Clock} as a bean so time-dependent services can be
 * unit-tested with a fixed clock instead of calling {@code LocalDate.now()} directly.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
