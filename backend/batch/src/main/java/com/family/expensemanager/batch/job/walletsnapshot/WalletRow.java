package com.family.expensemanager.batch.job.walletsnapshot;

import java.math.BigDecimal;

/**
 * Reader output: the WALLETS columns the snapshot needs.
 *
 * @author boyquynhluu
 */
public record WalletRow(long id, long familyId, String name, String currency, BigDecimal initialBalance) {
}
