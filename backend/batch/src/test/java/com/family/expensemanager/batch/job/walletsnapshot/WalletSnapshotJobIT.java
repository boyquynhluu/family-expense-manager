package com.family.expensemanager.batch.job.walletsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs walletMonthlySnapshotJob end to end on a real MySQL: fem_batch (Spring Batch metadata + the snapshot table,
 * from this module's Flyway migrations) and a fem_expense copy built from expense-service's own migrations, read
 * by a SELECT-only user exactly as in production. Run by failsafe (`mvn verify`), needs Docker.
 */
@SpringBatchTest
@SpringBootTest
@Testcontainers
class WalletSnapshotJobIT {

    private static final String EXPENSE_DB = "fem_expense_it";
    private static final String SEPTEMBER = "2026-09";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("fem_batch")
            .withUsername("fem_batch")
            .withPassword("test");

    /** Root on fem_expense_it, one connection (keeps FOREIGN_KEY_CHECKS and lets inserts return their id). */
    private static SingleConnectionDataSource expenseRoot;
    private static JdbcTemplate expense;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(WalletSnapshotJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private DataSource dataSource; // primary = fem_batch

    private long categoryId;

    @DynamicPropertySource
    static void datasources(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Production setup: Flyway creates BATCH_* and the snapshot table (the H2 test config turns it off).
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
        registry.add("fem.expense.datasource.url", WalletSnapshotJobIT::expenseUrl);
        registry.add("fem.expense.datasource.username", MYSQL::getUsername);
        registry.add("fem.expense.datasource.password", MYSQL::getPassword);
        registry.add("fem.expense.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeAll
    static void createExpenseDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + EXPENSE_DB);
            // The job's user may only read business data — as fem_batch_ro in production.
            statement.execute("GRANT SELECT ON " + EXPENSE_DB + ".* TO '" + MYSQL.getUsername() + "'@'%'");
        }
        Flyway.configure()
                .dataSource(expenseUrl(), "root", MYSQL.getPassword())
                .locations("filesystem:../expense-service/src/main/resources/db/migration")
                .load()
                .migrate();
        expenseRoot = new SingleConnectionDataSource(expenseUrl(), "root", MYSQL.getPassword(), true);
        expense = new JdbcTemplate(expenseRoot);
    }

    @AfterAll
    static void closeExpenseConnection() {
        if (expenseRoot != null) {
            expenseRoot.destroy();
        }
    }

    @BeforeEach
    void cleanState() {
        jobLauncherTestUtils.setJob(job);
        jobRepositoryTestUtils.removeJobExecutions();
        new JdbcTemplate(dataSource).update("DELETE FROM WALLET_MONTHLY_SNAPSHOTS");
        expense.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("LOAN_PAYMENTS", "LOANS", "WALLET_ADJUSTMENTS", "WALLET_TRANSFERS",
                "TRANSACTIONS", "WALLETS", "CATEGORIES")) {
            expense.execute("TRUNCATE TABLE " + table);
        }
        expense.execute("SET FOREIGN_KEY_CHECKS = 1");
        categoryId = insert("INSERT INTO CATEGORIES (family_id, name, type) VALUES (1, 'Chung', 'EXPENSE')");
    }

    @Test
    void september_countsEveryComponentDatedBeforeOctober_andRecordsASummary() throws Exception {
        long cash = wallet("Tiền mặt", "1000000", null);
        long bank = wallet("Ngân hàng", "0", null);
        transaction(cash, "INCOME", "500000", "2026-09-15 09:00:00", null);
        transaction(cash, "EXPENSE", "200000", "2026-09-20 12:00:00", null);
        transaction(cash, "EXPENSE", "100000", "2026-10-05 08:00:00", null);              // after the month
        transaction(cash, "EXPENSE", "50000", "2026-09-21 08:00:00", "2026-09-22 08:00:00"); // deleted
        insert("INSERT INTO WALLET_TRANSFERS (family_id, from_wallet_id, to_wallet_id, amount, occurred_at,"
                + " created_by_user_id, created_at) VALUES (1, ?, ?, 100000, '2026-09-25 10:00:00', 1, NOW())",
                cash, bank);
        insert("INSERT INTO WALLET_ADJUSTMENTS (family_id, wallet_id, amount, balance_before, balance_after,"
                + " occurred_at, created_by_user_id, created_at)"
                + " VALUES (1, ?, 30000, 0, 30000, '2026-09-28 10:00:00', 1, NOW())", cash);
        long loan = loan(cash, "BORROWED", "400000", "2026-09-10");
        loan(cash, "BORROWED", "999000", "2026-10-02");                                     // after the month
        insert("INSERT INTO LOAN_PAYMENTS (loan_id, family_id, wallet_id, amount, paid_at, created_by_user_id,"
                + " created_at) VALUES (?, 1, ?, 150000, '2026-09-29 10:00:00', 1, NOW())", loan, cash);

        JobExecution execution = jobLauncherTestUtils.launchJob(september());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("walletSnapshotPrepareStep", "walletSnapshotStep", "walletSnapshotVerifyStep");

        // 1,000,000 + 500,000 - 200,000 - 100,000 (transfer) + 30,000 + 400,000 (loan) - 150,000 (repayment)
        Map<String, Object> cashRow = snapshot(cash);
        assertThat((BigDecimal) cashRow.get("closing_balance")).isEqualByComparingTo("1480000");
        assertThat((BigDecimal) cashRow.get("income")).isEqualByComparingTo("500000");
        assertThat((BigDecimal) cashRow.get("expense")).isEqualByComparingTo("200000");
        assertThat((BigDecimal) cashRow.get("transfer_out")).isEqualByComparingTo("100000");
        assertThat((BigDecimal) cashRow.get("adjustments")).isEqualByComparingTo("30000");
        assertThat((BigDecimal) cashRow.get("loans")).isEqualByComparingTo("250000");
        assertThat(cashRow.get("wallet_name")).isEqualTo("Tiền mặt");
        assertThat((BigDecimal) snapshot(bank).get("closing_balance")).isEqualByComparingTo("100000");

        assertThat(execution.getExecutionContext().getLong("walletCount")).isEqualTo(2);
        assertThat(execution.getExecutionContext().getString("totalClosingBalance")).isEqualTo("1580000.00");
    }

    @Test
    void sameMonthTwice_isRefused_andLeavesTheSnapshotAsItWas() throws Exception {
        wallet("Tiền mặt", "100", null);
        assertThat(jobLauncherTestUtils.launchJob(september()).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(september()))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        assertThat(snapshotCount()).isEqualTo(1);
    }

    @Test
    void walletDeletedAfterTheMonth_isIncluded_deletedDuringIt_isNot() throws Exception {
        long deletedInOctober = wallet("Ví cũ", "500", "2026-10-10 10:00:00");
        wallet("Ví đã xoá", "700", "2026-09-10 10:00:00");

        jobLauncherTestUtils.launchJob(september());

        assertThat(snapshotCount()).isEqualTo(1);
        assertThat((BigDecimal) snapshot(deletedInOctober).get("closing_balance")).isEqualByComparingTo("500");
    }

    @Test
    void moreWalletsThanOnePage_areEachReadExactlyOnce() throws Exception {
        // 250 wallets = 3 pages of 100; every 10th one matches through the OR branch (deleted after the month),
        // which is what the parentheses in the reader's where clause protect.
        for (int i = 1; i <= 250; i++) {
            wallet("Ví " + i, "1", i % 10 == 0 ? "2026-10-15 00:00:00" : null);
        }

        JobExecution execution = jobLauncherTestUtils.launchJob(september());

        StepExecution chunkStep = execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals("walletSnapshotStep")).findFirst().orElseThrow();
        assertThat(chunkStep.getReadCount()).isEqualTo(250);
        assertThat(chunkStep.getWriteCount()).isEqualTo(250);
        assertThat(chunkStep.getCommitCount()).isEqualTo(3);
        assertThat(snapshotCount()).isEqualTo(250);
    }

    @Test
    void prepareStep_removesRowsLeftFromAnEarlierRunOfTheMonth() throws Exception {
        long cash = wallet("Tiền mặt", "100", null);
        insertSnapshotRow(999L, "5", "5"); // a wallet that no longer qualifies

        jobLauncherTestUtils.launchJob(september());

        assertThat(snapshotCount()).isEqualTo(1);
        assertThat(snapshot(cash)).isNotNull();
    }

    @Test
    void verifyStep_failsWhenAClosingBalanceIsNotTheSumOfItsComponents() {
        insertSnapshotRow(1L, "100", "999"); // components add up to 100

        JobExecution execution = jobLauncherTestUtils.launchStep("walletSnapshotVerifyStep", september());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions()).anySatisfy(e ->
                assertThat(e).isInstanceOf(IllegalStateException.class).hasMessageContaining("closing_balance"));
    }

    // ---------------------------------------------------------------- helpers

    private static String expenseUrl() {
        return MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + EXPENSE_DB);
    }

    private static JobParameters september() {
        return new JobParametersBuilder().addString("yearMonth", SEPTEMBER).toJobParameters();
    }

    private long wallet(String name, String initialBalance, String deletedAt) {
        return insert("INSERT INTO WALLETS (family_id, name, currency, initial_balance, deleted_at)"
                + " VALUES (1, ?, 'VND', ?, ?)", name, new BigDecimal(initialBalance), deletedAt);
    }

    private void transaction(long walletId, String type, String amount, String occurredAt, String deletedAt) {
        insert("INSERT INTO TRANSACTIONS (wallet_id, category_id, family_id, user_id, type, amount, occurred_at,"
                + " deleted_at) VALUES (?, ?, 1, 1, ?, ?, ?, ?)",
                walletId, categoryId, type, new BigDecimal(amount), occurredAt, deletedAt);
    }

    private long loan(long walletId, String direction, String principal, String startDate) {
        return insert("INSERT INTO LOANS (family_id, member_user_id, direction, counterparty_name, principal,"
                + " wallet_id, start_date, created_by_user_id, created_at) VALUES (1, 1, ?, 'Anh Ba', ?, ?, ?, 1, NOW())",
                direction, new BigDecimal(principal), walletId, startDate);
    }

    private void insertSnapshotRow(long walletId, String initialBalance, String closingBalance) {
        new JdbcTemplate(dataSource).update("INSERT INTO WALLET_MONTHLY_SNAPSHOTS (snapshot_month, wallet_id,"
                + " family_id, wallet_name, currency, initial_balance, income, expense, transfer_in, transfer_out,"
                + " adjustments, loans, closing_balance) VALUES (?, ?, 1, 'x', 'VND', ?, 0, 0, 0, 0, 0, 0, ?)",
                SEPTEMBER, walletId, new BigDecimal(initialBalance), new BigDecimal(closingBalance));
    }

    private Map<String, Object> snapshot(long walletId) {
        return new JdbcTemplate(dataSource).queryForMap(
                "SELECT * FROM WALLET_MONTHLY_SNAPSHOTS WHERE snapshot_month = ? AND wallet_id = ?",
                SEPTEMBER, walletId);
    }

    private long snapshotCount() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM WALLET_MONTHLY_SNAPSHOTS WHERE snapshot_month = ?", Long.class, SEPTEMBER);
    }

    /** Runs an INSERT on fem_expense_it and returns the generated id. */
    private static long insert(String sql, Object... args) {
        return expense.execute((Connection connection) -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < args.length; i++) {
                    statement.setObject(i + 1, args[i]);
                }
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }
}
