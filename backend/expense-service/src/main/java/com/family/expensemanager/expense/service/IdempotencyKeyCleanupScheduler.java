package com.family.expensemanager.expense.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Deletes {@code Idempotency-Key} records older than {@code idempotency.retention-hours} (default 24), once a
 * night — a key only has to outlive the client's retries, and without this IDEMPOTENCY_KEYS grew forever.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "IdempotencyKeyCleanupScheduler")
public class IdempotencyKeyCleanupScheduler {

    private final IdempotencyGuard idempotencyGuard;
    private final Clock clock;
    private final int retentionHours;

    public IdempotencyKeyCleanupScheduler(IdempotencyGuard idempotencyGuard, Clock clock,
                                          @Value("${idempotency.retention-hours:24}") int retentionHours) {
        this.idempotencyGuard = idempotencyGuard;
        this.clock = clock;
        this.retentionHours = retentionHours;
    }

    @Scheduled(cron = "${idempotency.purge-cron:0 45 3 * * *}")
    public void run() {
        log.info("run - start, retentionHours={}", retentionHours);
        int deleted = idempotencyGuard.purgeCreatedBefore(LocalDateTime.now(clock).minusHours(retentionHours));
        log.info("run - done, deleted={}", deleted);
    }
}
