package com.family.expensemanager.expense.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class IdempotencyKeyCleanupSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T20:45:00Z"), ZoneId.of("UTC"));

    @Test
    void run_purgesKeysOlderThanTheRetention() {
        IdempotencyGuard guard = mock(IdempotencyGuard.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 11, 20, 45);
        when(guard.purgeCreatedBefore(cutoff)).thenReturn(3);

        new IdempotencyKeyCleanupScheduler(guard, CLOCK, 24).run();

        verify(guard).purgeCreatedBefore(cutoff);
    }
}
