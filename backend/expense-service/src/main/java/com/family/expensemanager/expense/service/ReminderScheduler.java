package com.family.expensemanager.expense.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fires {@link ReminderService#runDaily()} every morning (README C1, C2, B3) and
 * {@link MonthlySummaryService#sendPreviousMonth()} on the first day of each month (README C7).
 *
 * @author boyquynhluu
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "ReminderScheduler")
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final MonthlySummaryService monthlySummaryService;

    @Scheduled(cron = "${reminders.cron:0 0 8 * * *}")
    public void daily() {
        log.info("daily - start");
        reminderService.runDaily();
    }

    @Scheduled(cron = "${monthly-summary.cron:0 0 7 1 * *}")
    public void monthly() {
        log.info("monthly - start");
        monthlySummaryService.sendPreviousMonth();
    }
}
