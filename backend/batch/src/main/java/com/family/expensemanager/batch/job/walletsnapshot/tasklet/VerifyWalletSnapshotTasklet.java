package com.family.expensemanager.batch.job.walletsnapshot.tasklet;

import java.math.BigDecimal;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.family.expensemanager.batch.dao.WalletBalanceDao;
import com.family.expensemanager.batch.job.walletsnapshot.WalletSnapshotPeriod;

import lombok.extern.slf4j.Slf4j;

/**
 * Step 3 of walletMonthlySnapshotJob: checks what the chunk step wrote and records a summary.
 * <ul>
 *   <li><b>Fails the job</b> if any row's closing balance is not the sum of its components — a write or formula
 *       bug; the snapshot must not be trusted. After fixing, a restart re-runs only this step.</li>
 *   <li><b>Warns</b> if the row count differs from the number of wallets that existed at the cutoff — a wallet
 *       created or deleted while the job ran explains that, so it is not an error.</li>
 *   <li>Puts {@code walletCount} and {@code totalClosingBalance} in the job's ExecutionContext (BATCH_JOB_EXECUTION_CONTEXT),
 *       where the run's history keeps them.</li>
 * </ul>
 *
 * @author boyquynhluu
 */
@Component
@StepScope
@Slf4j(topic = "VerifyWalletSnapshotTasklet")
public class VerifyWalletSnapshotTasklet implements Tasklet {

    static final String WALLET_COUNT = "walletCount";
    static final String TOTAL_CLOSING_BALANCE = "totalClosingBalance";

    private static final String SUMMARY_SQL = """
            SELECT COUNT(*)                          AS wallets,
                   COALESCE(SUM(closing_balance), 0) AS total,
                   COALESCE(SUM(CASE WHEN closing_balance <> initial_balance + income - expense + transfer_in
                                                             - transfer_out + adjustments + loans
                                     THEN 1 ELSE 0 END), 0) AS inconsistent
            FROM WALLET_MONTHLY_SNAPSHOTS
            WHERE snapshot_month = :month
            """;

    private final NamedParameterJdbcTemplate batchJdbc;
    private final WalletBalanceDao walletBalanceDao;
    private final WalletSnapshotPeriod period;

    public VerifyWalletSnapshotTasklet(DataSource dataSource, WalletBalanceDao walletBalanceDao,
                                       @Value("#{jobParameters['yearMonth']}") String yearMonth) {
        this.batchJdbc = new NamedParameterJdbcTemplate(dataSource);
        this.walletBalanceDao = walletBalanceDao;
        this.period = WalletSnapshotPeriod.of(yearMonth);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Map<String, Object> summary = batchJdbc.queryForMap(SUMMARY_SQL, Map.of("month", period.key()));
        long wallets = ((Number) summary.get("wallets")).longValue();
        BigDecimal total = new BigDecimal(summary.get("total").toString());
        long inconsistent = ((Number) summary.get("inconsistent")).longValue();

        if (inconsistent > 0) {
            throw new IllegalStateException("Snapshot " + period.key() + ": " + inconsistent
                    + " row(s) whose closing_balance is not the sum of its components");
        }
        long expected = walletBalanceDao.countWalletsAt(period.cutoff());
        if (wallets != expected) {
            log.warn("execute - month={}: {} snapshot row(s) but {} wallet(s) existed at the cutoff",
                    period.key(), wallets, expected);
        }

        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution()
                .getExecutionContext();
        jobContext.putLong(WALLET_COUNT, wallets);
        // As a string: the context is stored as JSON, and a plain string round-trips a DECIMAL exactly.
        jobContext.putString(TOTAL_CLOSING_BALANCE, total.toPlainString());
        log.info("execute - month={}, wallets={}, totalClosingBalance={}", period.key(), wallets,
                total.toPlainString());
        return RepeatStatus.FINISHED;
    }
}
