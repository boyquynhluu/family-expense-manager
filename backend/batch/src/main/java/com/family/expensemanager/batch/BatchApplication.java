package com.family.expensemanager.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Run-once batch process (README "Batch"). Spring Boot's batch auto-configuration does the launching:
 * <ul>
 *   <li>{@code --spring.batch.job.name=<jobBeanName>} picks the job (required — there is more than one);</li>
 *   <li>plain {@code key=value} arguments become its JobParameters (e.g. {@code yearMonth=2026-09});</li>
 *   <li>{@link SpringApplication#exit} turns the job's status into the process exit code (0 = COMPLETED,
 *       5 = FAILED, 1 = could not start — e.g. that job instance already COMPLETED), which
 *       infra/batch/bin/run-java-job.sh and cron rely on.</li>
 * </ul>
 *
 * @author boyquynhluu
 */
@SpringBootApplication
public class BatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
    }
}
