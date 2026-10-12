package com.family.expensemanager.batch.common.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared by every job ({@code .listener(jobLoggingListener)} in its JobConfig): one line when the job starts and
 * one summary line per step plus the outcome when it ends — what you grep for in /var/log/fem-batch/*.log.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "JobLoggingListener")
public class JobLoggingListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job {} started, parameters={}", jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String job = jobExecution.getJobInstance().getJobName();
        for (StepExecution step : jobExecution.getStepExecutions()) {
            log.info("Job {} step {}: status={}, read={}, written={}, filtered={}, skipped={}", job,
                    step.getStepName(), step.getStatus(), step.getReadCount(), step.getWriteCount(),
                    step.getFilterCount(), step.getSkipCount());
        }
        log.info("Job {} finished: status={}, exitCode={}, duration={}", job, jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                duration(jobExecution.getStartTime(), jobExecution.getEndTime()));
        jobExecution.getAllFailureExceptions()
                .forEach(e -> log.error("Job {} failure", job, e));
    }

    private static Duration duration(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? Duration.ZERO : Duration.between(start, end);
    }
}
