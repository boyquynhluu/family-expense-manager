package com.family.expensemanager.expense.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * @author boyquynhluu
 */
class TrashRetentionSchedulerTest {

    @Test
    void run_purgesWhatHasBeenInTheTrashLongerThanTheRetentionDays() {
        TrashService trashService = mock(TrashService.class);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T20:30:00Z"), zone); // 03:30 on 3 Oct, local

        new TrashRetentionScheduler(trashService, clock, 30).run();

        verify(trashService).purgeDeletedBefore(LocalDateTime.of(2026, 9, 3, 3, 30));
    }
}
