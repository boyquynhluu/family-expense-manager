package com.family.expensemanager.batch.job.walletsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.family.expensemanager.batch.dao.WalletBalanceDao;

class WalletSnapshotProcessorTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDate CUTOFF_DATE = LocalDate.of(2026, 10, 1);

    private final WalletBalanceDao dao = mock(WalletBalanceDao.class);
    private final WalletSnapshotProcessor processor = new WalletSnapshotProcessor(dao, "2026-09");

    @Test
    void closingBalance_followsWalletServiceFormula_withEveryComponentBeforeTheCutoff() {
        // Distinct powers of ten: the total is only right if every component has the right sign.
        when(dao.sumTransactions(7L, "INCOME", CUTOFF)).thenReturn(new BigDecimal("10"));
        when(dao.sumTransactions(7L, "EXPENSE", CUTOFF)).thenReturn(new BigDecimal("100"));
        when(dao.sumTransfersIn(7L, CUTOFF)).thenReturn(new BigDecimal("1000"));
        when(dao.sumTransfersOut(7L, CUTOFF)).thenReturn(new BigDecimal("10000"));
        when(dao.sumAdjustments(7L, CUTOFF)).thenReturn(new BigDecimal("100000"));
        when(dao.sumLoanFlows(7L, CUTOFF_DATE, CUTOFF)).thenReturn(new BigDecimal("1000000"));

        WalletSnapshot snapshot = processor.process(
                new WalletRow(7L, 3L, "Tiền mặt", "VND", new BigDecimal("1")));

        // 1 + 10 - 100 + 1000 - 10000 + 100000 + 1000000
        assertThat(snapshot.closingBalance()).isEqualByComparingTo("1090911");
        assertThat(snapshot.snapshotMonth()).isEqualTo("2026-09");
        assertThat(snapshot.walletId()).isEqualTo(7L);
        assertThat(snapshot.familyId()).isEqualTo(3L);
        assertThat(snapshot.walletName()).isEqualTo("Tiền mặt");
        assertThat(snapshot.income()).isEqualByComparingTo("10");
        assertThat(snapshot.expense()).isEqualByComparingTo("100");
        assertThat(snapshot.transferIn()).isEqualByComparingTo("1000");
        assertThat(snapshot.transferOut()).isEqualByComparingTo("10000");
        assertThat(snapshot.adjustments()).isEqualByComparingTo("100000");
        assertThat(snapshot.loans()).isEqualByComparingTo("1000000");
    }

    @Test
    void periodCutoff_isTheFirstMomentOfTheNextMonth_evenAcrossTheYear() {
        WalletSnapshotPeriod december = WalletSnapshotPeriod.of("2026-12");
        assertThat(december.cutoff()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
        assertThat(december.cutoffDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(december.key()).isEqualTo("2026-12");
    }
}
