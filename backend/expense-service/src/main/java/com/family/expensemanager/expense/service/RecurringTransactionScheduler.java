package com.family.expensemanager.expense.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Fires {@link RecurringTransactionService#generateDueTransactions()} once a day. */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "RecurringTransactionScheduler")
public class RecurringTransactionScheduler {

    private final RecurringTransactionService recurringTransactionService;

    @Scheduled(cron = "${recurring-transactions.cron:0 0 1 * * *}")
    public void run() {
        log.info("run - start");
        recurringTransactionService.generateDueTransactions();
    }
}
