package com.family.expensemanager.expense.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Removes for good whatever has sat in the trash longer than {@code trash.retention-days} (default 30), once a
 * night, through the same per-row rules as the "Xoá vĩnh viễn" / "Dọn sạch thùng rác" buttons (README mục 10).
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "TrashRetentionScheduler")
public class TrashRetentionScheduler {

    private final TrashService trashService;
    private final Clock clock;
    private final int retentionDays;

    public TrashRetentionScheduler(TrashService trashService, Clock clock,
                                   @Value("${trash.retention-days:30}") int retentionDays) {
        this.trashService = trashService;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${trash.purge-cron:0 30 3 * * *}")
    public void run() {
        log.info("run - start, retentionDays={}", retentionDays);
        trashService.purgeDeletedBefore(LocalDateTime.now(clock).minusDays(retentionDays));
    }
}
