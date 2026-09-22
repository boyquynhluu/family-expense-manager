package com.family.expensemanager.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Daily removal of accounts that never verified their email, see {@link AuthService#purgeStaleUnverifiedAccounts(int)}. */
@Component
@Slf4j(topic = "UnverifiedAccountCleanupScheduler")
public class UnverifiedAccountCleanupScheduler {

    private final AuthService authService;
    private final int retentionDays;

    public UnverifiedAccountCleanupScheduler(
            AuthService authService,
            @Value("${auth.unverified-cleanup.retention-days:7}") int retentionDays) {
        this.authService = authService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${auth.unverified-cleanup.cron:0 30 2 * * *}")
    public void run() {
        try {
            int purged = authService.purgeStaleUnverifiedAccounts(retentionDays);
            log.info("run - đã xoá {} tài khoản chưa xác thực email quá {} ngày sau khi token hết hạn", purged, retentionDays);
        } catch (RuntimeException e) {
            log.error("run - dọn tài khoản chưa xác thực thất bại", e);
        }
    }
}
