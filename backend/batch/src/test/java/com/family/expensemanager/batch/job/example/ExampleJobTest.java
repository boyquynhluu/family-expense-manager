package com.family.expensemanager.batch.job.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TEMPLATE — how to test a job end to end: launch it with JobLauncherTestUtils and assert on its status and
 * step counts. Copy along with the job package; switch to Testcontainers MySQL once the job touches real tables.
 */
@SpringBatchTest
@SpringBootTest
class ExampleJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ExampleJobConfig.JOB_NAME)
    private Job exampleJob;

    @BeforeEach
    void selectJob() {
        // Explicit: once there are several Job beans, JobLauncherTestUtils cannot pick one on its own.
        jobLauncherTestUtils.setJob(exampleJob);
    }

    @Test
    void exampleJob_readsProcessesAndWritesEveryItem() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(3);
        assertThat(step.getWriteCount()).isEqualTo(3);
    }
}
