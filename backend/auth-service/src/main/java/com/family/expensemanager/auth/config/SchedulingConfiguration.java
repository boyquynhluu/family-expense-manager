package com.family.expensemanager.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} for {@code UnverifiedAccountCleanupScheduler}. */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
