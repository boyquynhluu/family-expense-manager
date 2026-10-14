package com.family.expensemanager.batch.job.walletsnapshot;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import com.family.expensemanager.batch.common.listener.JobLoggingListener;
import com.family.expensemanager.batch.job.walletsnapshot.tasklet.PrepareWalletSnapshotTasklet;
import com.family.expensemanager.batch.job.walletsnapshot.tasklet.VerifyWalletSnapshotTasklet;

/**
 * walletMonthlySnapshotJob — every wallet's balance at the end of a month, into fem_batch.WALLET_MONTHLY_SNAPSHOTS.
 * Reads fem_expense (SELECT-only user), writes only fem_batch.
 *
 * <pre>
 *   walletSnapshotPrepareStep  (tasklet) remove earlier rows of the month
 *   walletSnapshotStep         (chunk)   WALLETS → balance at the cutoff → upsert, {@value #CHUNK_SIZE} per transaction
 *   walletSnapshotVerifyStep   (tasklet) consistency check + summary in the job's ExecutionContext
 * </pre>
 *
 * <p>Parameter {@code yearMonth=yyyy-MM} (a month that has ended) identifies the run — no incrementer: the same
 * month cannot run twice by mistake (exit code 1), and a FAILED run launched again with the same parameter resumes
 * from the last committed chunk, skipping the steps already COMPLETED.
 *
 * <p>Known limit: WALLETS has no created_at, so a wallet created after the month still gets a row (its initial
 * balance, no movements).
 *
 * <p>Run: {@code infra/batch/bin/wallet-snapshot.sh [yyyy-MM]} (cron, last month by default), or directly
 * {@code infra/batch/bin/run-java-job.sh walletMonthlySnapshotJob yearMonth=2026-09}.
 *
 * @author boyquynhluu
 */
@Configuration
public class WalletSnapshotJobConfig {

    static final String JOB_NAME = "walletMonthlySnapshotJob";
    static final int CHUNK_SIZE = 100;
    /** "Has this month ended?" is decided in Vietnam time, not the container's UTC (see YearMonthParameterValidator). */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String UPSERT_SQL = """
            INSERT INTO WALLET_MONTHLY_SNAPSHOTS
                (snapshot_month, wallet_id, family_id, wallet_name, currency, initial_balance, income, expense,
                 transfer_in, transfer_out, adjustments, loans, closing_balance)
            VALUES
                (:snapshotMonth, :walletId, :familyId, :walletName, :currency, :initialBalance, :income, :expense,
                 :transferIn, :transferOut, :adjustments, :loans, :closingBalance)
            ON DUPLICATE KEY UPDATE
                family_id = VALUES(family_id), wallet_name = VALUES(wallet_name), currency = VALUES(currency),
                initial_balance = VALUES(initial_balance), income = VALUES(income), expense = VALUES(expense),
                transfer_in = VALUES(transfer_in), transfer_out = VALUES(transfer_out),
                adjustments = VALUES(adjustments), loans = VALUES(loans), closing_balance = VALUES(closing_balance),
                updated_at = NOW()
            """;

    // ---------------------------------------------------------------- job

    @Bean
    public Job walletMonthlySnapshotJob(JobRepository jobRepository,
                                        @Qualifier("walletSnapshotPrepareStep") Step prepareStep,
                                        @Qualifier("walletSnapshotStep") Step snapshotStep,
                                        @Qualifier("walletSnapshotVerifyStep") Step verifyStep,
                                        JobLoggingListener jobLoggingListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new YearMonthParameterValidator(Clock.system(BUSINESS_ZONE)))
                .listener(jobLoggingListener)
                .start(prepareStep)
                .next(snapshotStep)
                .next(verifyStep)
                .build();
    }

    // ---------------------------------------------------------------- steps

    @Bean
    public Step walletSnapshotPrepareStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                          PrepareWalletSnapshotTasklet tasklet) {
        return new StepBuilder("walletSnapshotPrepareStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step walletSnapshotStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   JdbcPagingItemReader<WalletRow> walletSnapshotReader,
                                   WalletSnapshotProcessor walletSnapshotProcessor,
                                   JdbcBatchItemWriter<WalletSnapshot> walletSnapshotWriter) {
        return new StepBuilder("walletSnapshotStep", jobRepository)
                .<WalletRow, WalletSnapshot>chunk(CHUNK_SIZE, transactionManager)
                .reader(walletSnapshotReader)
                .processor(walletSnapshotProcessor)
                .writer(walletSnapshotWriter)
                // A dropped connection or lock timeout is retried; anything else fails the step (no skip: a
                // snapshot with silently missing wallets is worse than a failed run that can be restarted).
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .build();
    }

    @Bean
    public Step walletSnapshotVerifyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                         VerifyWalletSnapshotTasklet tasklet) {
        return new StepBuilder("walletSnapshotVerifyStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    // ---------------------------------------------------------------- reader / writer

    /**
     * Wallets that existed at the cutoff (not deleted, or deleted after it), page by page in id order — keyset
     * paging ({@code AND id > last id}), never OFFSET.
     *
     * <p>Two details that matter:
     * <ul>
     *   <li>the where clause is in parentheses: the paging appends {@code AND id > ?}, and without them the
     *       {@code OR} would swallow it, so page 2 onwards would read the undeleted wallets again;</li>
     *   <li>the return type is the concrete reader, not ItemReader: the bean is a step-scoped proxy, and only a
     *       type that shows it is an ItemStream gets its position saved — which is what lets a restart resume.</li>
     * </ul>
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<WalletRow> walletSnapshotReader(
            @Qualifier("expenseDataSource") DataSource expenseDataSource,
            @Value("#{jobParameters['yearMonth']}") String yearMonth) {
        WalletSnapshotPeriod period = WalletSnapshotPeriod.of(yearMonth);
        return new JdbcPagingItemReaderBuilder<WalletRow>()
                .name("walletSnapshotReader")
                .dataSource(expenseDataSource)
                .selectClause("SELECT id, family_id, name, currency, initial_balance")
                .fromClause("FROM WALLETS")
                .whereClause("(deleted_at IS NULL OR deleted_at >= :cutoff)")
                .parameterValues(Map.of("cutoff", period.cutoff()))
                .sortKeys(Map.of("id", Order.ASCENDING))
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) -> new WalletRow(
                        rs.getLong("id"),
                        rs.getLong("family_id"),
                        rs.getString("name"),
                        rs.getString("currency"),
                        rs.getBigDecimal("initial_balance")))
                .build();
    }

    /**
     * Batch upsert into fem_batch (the primary DataSource, so it commits with the chunk). Idempotent on
     * (snapshot_month, wallet_id): a re-written chunk updates its rows instead of duplicating them.
     * SimplePropertySqlParameterSource reads record accessors — beanMapped() looks for getX() and finds nothing.
     */
    @Bean
    public JdbcBatchItemWriter<WalletSnapshot> walletSnapshotWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<WalletSnapshot>()
                .dataSource(dataSource)
                .sql(UPSERT_SQL)
                .itemSqlParameterSourceProvider(SimplePropertySqlParameterSource::new)
                .build();
    }
}
