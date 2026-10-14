package com.family.expensemanager.batch.job.walletsnapshot;

import java.math.BigDecimal;

/**
 * Processor output, one WALLET_MONTHLY_SNAPSHOTS row. Component names match the writer's named parameters
 * (camelCase of the columns), which read them through the record accessors.
 *
 * @author boyquynhluu
 */
public record WalletSnapshot(
        String snapshotMonth,
        long walletId,
        long familyId,
        String walletName,
        String currency,
        BigDecimal initialBalance,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal transferIn,
        BigDecimal transferOut,
        BigDecimal adjustments,
        BigDecimal loans,
        BigDecimal closingBalance) {
}
