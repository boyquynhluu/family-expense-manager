package com.family.expensemanager.batch.job.example;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.family.expensemanager.batch.common.listener.JobLoggingListener;

/**
 * TEMPLATE — copy this package to start a new job (README "Batch" → "Thêm một job mới"), then delete it.
 *
 * <p>One package per job ({@code job/<name>/}): this JobConfig declares the Job and its Step(s); the reader,
 * processor and writer are separate classes next to it. Chunk-oriented: items are read one by one, processed,
 * and written {@link #CHUNK_SIZE} at a time, each chunk in its own transaction — a failure only rolls back the
 * current chunk, and a restart resumes after the last committed one.
 *
 * <p>Run: {@code infra/batch/bin/run-java-job.sh exampleJob}
 *
 * @author boyquynhluu
 */
@Configuration
public class ExampleJobConfig {

    static final String JOB_NAME = "exampleJob";
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job exampleJob(JobRepository jobRepository, Step exampleStep, JobLoggingListener jobLoggingListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                // RunIdIncrementer: a new run.id each launch, so this demo can run again and again. A real job keyed
                // by its parameters (e.g. yearMonth=2026-09) should NOT have one — then the same month cannot
                // run twice by mistake, and a FAILED run is restarted from where it stopped instead.
                .incrementer(new RunIdIncrementer())
                .listener(jobLoggingListener)
                .start(exampleStep)
                .build();
    }

    @Bean
    public Step exampleStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            ExampleReader reader, ExampleProcessor processor, ExampleWriter writer) {
        return new StepBuilder("exampleStep", jobRepository)
                .<ExampleItem, ExampleItem>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                // For real jobs decide explicitly what a bad item does, e.g.:
                // .faultTolerant().skip(InvalidRowException.class).skipLimit(10)
                //                 .retry(TransientDataAccessException.class).retryLimit(3)
                .build();
    }
}
