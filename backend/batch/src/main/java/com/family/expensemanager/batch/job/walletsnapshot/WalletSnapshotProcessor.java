package com.family.expensemanager.batch.job.walletsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.family.expensemanager.batch.dao.WalletBalanceDao;

/**
 * Computes one wallet's balance at the end of the month with the same formula as expense-service's
 * {@code WalletService.currentBalanceOf}: initial balance + income - expense + transfers in - transfers out
 * + adjustments + loan flows — each component limited to what is dated before the cutoff.
 *
 * <p>Six queries per wallet (the components live in different tables); fine for this data size, and it keeps each
 * SQL file a one-to-one copy of the service's. {@code @StepScope}: the month comes from the job parameters, which
 * only exist while the step runs.
 *
 * @author boyquynhluu
 */
@Component
@StepScope
public class WalletSnapshotProcessor implements ItemProcessor<WalletRow, WalletSnapshot> {

    static final String TYPE_INCOME = "INCOME";
    static final String TYPE_EXPENSE = "EXPENSE";

    private final WalletBalanceDao walletBalanceDao;
    private final String snapshotMonth;
    private final LocalDate cutoffDate;
    private final LocalDateTime cutoff;

    public WalletSnapshotProcessor(WalletBalanceDao walletBalanceDao,
                                   @Value("#{jobParameters['yearMonth']}") String yearMonth) {
        WalletSnapshotPeriod period = WalletSnapshotPeriod.of(yearMonth);
        this.walletBalanceDao = walletBalanceDao;
        this.snapshotMonth = period.key();
        this.cutoffDate = period.cutoffDate();
        this.cutoff = period.cutoff();
    }

    @Override
    public WalletSnapshot process(WalletRow wallet) {
        long walletId = wallet.id();
        BigDecimal income = walletBalanceDao.sumTransactions(walletId, TYPE_INCOME, cutoff);
        BigDecimal expense = walletBalanceDao.sumTransactions(walletId, TYPE_EXPENSE, cutoff);
        BigDecimal transferIn = walletBalanceDao.sumTransfersIn(walletId, cutoff);
        BigDecimal transferOut = walletBalanceDao.sumTransfersOut(walletId, cutoff);
        BigDecimal adjustments = walletBalanceDao.sumAdjustments(walletId, cutoff);
        BigDecimal loans = walletBalanceDao.sumLoanFlows(walletId, cutoffDate, cutoff);

        BigDecimal closing = wallet.initialBalance().add(income).subtract(expense).add(transferIn)
                .subtract(transferOut).add(adjustments).add(loans);

        return new WalletSnapshot(snapshotMonth, walletId, wallet.familyId(), wallet.name(), wallet.currency(),
                wallet.initialBalance(), income, expense, transferIn, transferOut, adjustments, loans, closing);
    }
}
