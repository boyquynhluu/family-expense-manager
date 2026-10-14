package com.family.expensemanager.batch.job.walletsnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * The month being snapshotted, from the {@code yearMonth} job parameter. One place for the cutoff so the reader,
 * the processor and both tasklets agree on it: everything dated before the first moment of the next month counts.
 * Dates are compared as stored (local wall-clock DATETIME), the same way the app's monthly reports do.
 *
 * @author boyquynhluu
 */
public record WalletSnapshotPeriod(YearMonth month) {

    public static WalletSnapshotPeriod of(String yearMonth) {
        return new WalletSnapshotPeriod(YearMonth.parse(yearMonth));
    }

    /** {@code yyyy-MM}, the value stored in WALLET_MONTHLY_SNAPSHOTS.snapshot_month. */
    public String key() {
        return month.toString();
    }

    /** First day of the next month — for DATE columns. */
    public LocalDate cutoffDate() {
        return month.plusMonths(1).atDay(1);
    }

    /** First moment of the next month — for DATETIME columns. */
    public LocalDateTime cutoff() {
        return cutoffDate().atStartOfDay();
    }
}
