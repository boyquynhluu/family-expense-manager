package com.family.expensemanager.batch.job.walletsnapshot.tasklet;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.family.expensemanager.batch.job.walletsnapshot.WalletSnapshotPeriod;

import lombok.extern.slf4j.Slf4j;

/**
 * Step 1 of walletMonthlySnapshotJob: removes any earlier snapshot rows of the month, so the chunk step writes the
 * month's exact set — a wallet that no longer qualifies (e.g. found to be deleted before the cutoff) does not
 * linger from a previous run.
 *
 * <p>Runs once per job instance: on a restart after a failure Spring Batch skips this COMPLETED step and resumes
 * the chunk step where it stopped, so the rows already written are kept. Re-doing a finished month on purpose
 * takes a new instance — an extra parameter, e.g. {@code yearMonth=2026-09 rerun=2}.
 *
 * <p>Uses the primary (fem_batch) DataSource, so the DELETE commits with the step's transaction.
 *
 * @author boyquynhluu
 */
@Component
@StepScope
@Slf4j(topic = "PrepareWalletSnapshotTasklet")
public class PrepareWalletSnapshotTasklet implements Tasklet {

    private final NamedParameterJdbcTemplate batchJdbc;
    private final WalletSnapshotPeriod period;

    public PrepareWalletSnapshotTasklet(DataSource dataSource,
                                        @Value("#{jobParameters['yearMonth']}") String yearMonth) {
        this.batchJdbc = new NamedParameterJdbcTemplate(dataSource);
        this.period = WalletSnapshotPeriod.of(yearMonth);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deleted = batchJdbc.update("DELETE FROM WALLET_MONTHLY_SNAPSHOTS WHERE snapshot_month = :month",
                Map.of("month", period.key()));
        log.info("execute - month={}, cutoff={}, removed {} earlier snapshot row(s)", period.key(), period.cutoff(),
                deleted);
        return RepeatStatus.FINISHED;
    }
}
