package com.family.expensemanager.batch.job.walletsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fast wiring check (H2, part of `mvn test`): the application context starts with both DataSources and the job has
 * its three steps in order. The job itself runs against MySQL in {@link WalletSnapshotJobIT}.
 */
@SpringBootTest
class WalletSnapshotJobConfigTest {

    @Autowired
    @Qualifier(WalletSnapshotJobConfig.JOB_NAME)
    private Job job;

    @Test
    void job_runsPrepareThenSnapshotThenVerify() {
        assertThat(job.isRestartable()).isTrue();
        assertThat(job.getJobParametersIncrementer()).as("no incrementer: yearMonth identifies the run").isNull();
        assertThat(((SimpleJob) job).getStepNames())
                .containsExactly("walletSnapshotPrepareStep", "walletSnapshotStep", "walletSnapshotVerifyStep");
    }
}
