package com.family.expensemanager.batch.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.seasar.doma.Dao;
import org.seasar.doma.Select;

/**
 * Read-only queries on fem_expense for walletMonthlySnapshotJob: each wallet-balance component up to a cutoff.
 * Every SQL file is the expense-service query that {@code WalletService.currentBalanceOf} uses (same file name
 * noted in each), plus a "before cutoff" condition — keep the two in step when the balance formula changes.
 *
 * @author boyquynhluu
 */
@Dao
public interface WalletBalanceDao {

    /** TransactionDao/sumAmountByWalletAndType.sql, transactions dated before the cutoff. */
    @Select
    BigDecimal sumTransactions(Long walletId, String type, LocalDateTime cutoff);

    /** WalletTransferDao/sumAmountIntoWallet.sql, before the cutoff. */
    @Select
    BigDecimal sumTransfersIn(Long walletId, LocalDateTime cutoff);

    /** WalletTransferDao/sumAmountFromWallet.sql, before the cutoff. */
    @Select
    BigDecimal sumTransfersOut(Long walletId, LocalDateTime cutoff);

    /** WalletAdjustmentDao/sumAmountByWalletId.sql, before the cutoff. */
    @Select
    BigDecimal sumAdjustments(Long walletId, LocalDateTime cutoff);

    /**
     * LoanDao/sumNetFlowForWallet.sql: loans started before {@code cutoffDate} (LOANS.start_date is a DATE) and
     * payments made before {@code cutoff}.
     */
    @Select
    BigDecimal sumLoanFlows(Long walletId, LocalDate cutoffDate, LocalDateTime cutoff);

    /** Wallets that existed at the cutoff — the same filter as the job's reader (verify step). */
    @Select
    long countWalletsAt(LocalDateTime cutoff);
}
