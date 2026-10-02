package com.family.expensemanager.expense.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.TransactionAuditLog;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletTransfer;
import com.zaxxer.hikari.HikariDataSource;

import org.seasar.doma.jdbc.Config;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * README "11. Chỉ có unit test (mock DAO), không có integration test chạm DB thật" —
 * runs the real Doma DAOs against a real MySQL (Testcontainers) with the actual Flyway
 * migrations applied, no mocked DAOs. Focuses on the insert/soft-delete/restore flow
 * added this round (README "10. Xoá là mất vĩnh viễn") since that's the newest, riskiest
 * schema change (deleted_at added to 3 tables, every SELECT had to be updated to filter
 * it) — exactly the class of change a mocked-DAO unit test can't verify end to end.
 *
 * Excluded from the fast {@code mvn test} unit-test loop (see maven-failsafe-plugin in
 * pom.xml) — runs via {@code mvn verify}, needs a local Docker daemon.
 *
 * @author boyquynhluu
 */
@Testcontainers
class ExpenseDaoIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("fem_expense_it")
            .withUsername("fem_expense")
            .withPassword("test");

    static Config domaConfig;

    @BeforeAll
    static void migrateAndConfigureDoma() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        domaConfig = new AppDomaConfig(dataSource);
    }

    private Wallet insertWallet(WalletDao walletDao, Long familyId, String name) {
        Wallet wallet = new Wallet();
        wallet.setFamilyId(familyId);
        wallet.setName(name);
        wallet.setCurrency("VND");
        wallet.setInitialBalance(BigDecimal.ZERO);
        walletDao.insert(wallet);
        return wallet;
    }

    private Category insertCategory(CategoryDao categoryDao, Long familyId, String name) {
        Category category = new Category();
        category.setFamilyId(familyId);
        category.setName(name);
        category.setType("EXPENSE");
        category.setIcon("food");
        category.setColor("#ff0000");
        categoryDao.insert(category);
        return category;
    }

    @Test
    void walletAndCategoryInsert_roundTripCorrectly_andExcludeSoftDeletedRows() {
        WalletDao walletDao = new WalletDaoImpl(domaConfig);
        CategoryDao categoryDao = new CategoryDaoImpl(domaConfig);
        Long familyId = 100L;

        Wallet wallet = insertWallet(walletDao, familyId, "Ví IT Test");
        Category category = insertCategory(categoryDao, familyId, "Danh mục IT Test");

        assertThat(wallet.getId()).isNotNull();
        assertThat(category.getId()).isNotNull();
        assertThat(walletDao.selectByFamilyId(familyId)).extracting(Wallet::getId).contains(wallet.getId());
        assertThat(categoryDao.selectByFamilyId(familyId)).extracting(Category::getId).contains(category.getId());

        // Soft-delete (README "10.") must hide the row from normal listing/lookup...
        wallet.setDeletedAt(LocalDateTime.now());
        walletDao.update(wallet);
        assertThat(walletDao.selectById(wallet.getId())).isEmpty();
        assertThat(walletDao.selectByFamilyId(familyId)).extracting(Wallet::getId).doesNotContain(wallet.getId());

        // ...but restore must bring it back exactly.
        int restored = walletDao.restore(wallet.getId(), familyId);
        assertThat(restored).isEqualTo(1);
        assertThat(walletDao.selectById(wallet.getId())).isPresent();
        assertThat(walletDao.selectByFamilyId(familyId)).extracting(Wallet::getId).contains(wallet.getId());
    }

    @Test
    void transactionInsert_roundTripsReceiptAndSoftDeleteColumns_correctly() {
        WalletDao walletDao = new WalletDaoImpl(domaConfig);
        CategoryDao categoryDao = new CategoryDaoImpl(domaConfig);
        TransactionDao transactionDao = new TransactionDaoImpl(domaConfig);
        Long familyId = 200L;

        Wallet wallet = insertWallet(walletDao, familyId, "Ví Giao Dịch IT");
        Category category = insertCategory(categoryDao, familyId, "Danh Mục Giao Dịch IT");

        Transaction transaction = new Transaction();
        transaction.setWalletId(wallet.getId());
        transaction.setCategoryId(category.getId());
        transaction.setFamilyId(familyId);
        transaction.setUserId(1L);
        transaction.setType("EXPENSE");
        transaction.setAmount(new BigDecimal("125000.00"));
        transaction.setOccurredAt(LocalDateTime.now().withNano(0));
        transaction.setNote("IT test transaction");
        transaction.setReceiptPath("100/1-receipt.jpg");
        transaction.setReceiptContentType("image/jpeg");
        transaction.setCreatedByName("Người Tạo IT");
        transactionDao.insert(transaction);

        assertThat(transaction.getId()).isNotNull();

        Transaction loaded = transactionDao.selectById(transaction.getId()).orElseThrow();
        assertThat(loaded.getAmount()).isEqualByComparingTo("125000.00");
        assertThat(loaded.getReceiptPath()).isEqualTo("100/1-receipt.jpg");
        assertThat(loaded.getCreatedByName()).isEqualTo("Người Tạo IT");
        assertThat(loaded.getDeletedAt()).isNull();
        assertThat(transactionDao.countByWalletId(wallet.getId())).isEqualTo(1);

        // Soft-deleting a transaction must drop it from balance/report aggregates too.
        transaction.setDeletedAt(LocalDateTime.now());
        transaction.setDeletedByUserId(7L);
        transaction.setDeletedByName("Người Xoá IT");
        transactionDao.update(transaction);
        assertThat(transactionDao.selectById(transaction.getId())).isEmpty();
        // V12: who deleted it survives the round trip...
        Transaction trashed = transactionDao.selectDeletedById(transaction.getId()).orElseThrow();
        assertThat(trashed.getDeletedByUserId()).isEqualTo(7L);
        assertThat(trashed.getDeletedByName()).isEqualTo("Người Xoá IT");
        assertThat(transactionDao.countByWalletId(wallet.getId())).isEqualTo(0);
        assertThat(transactionDao.countDeletedByFamilyId(familyId)).isGreaterThanOrEqualTo(1);
        assertThat(transactionDao.selectDeletedByFamilyIdPaged(familyId, 100, 0))
                .extracting(Transaction::getId)
                .contains(transaction.getId());

        int restored = transactionDao.restore(transaction.getId(), familyId);
        assertThat(restored).isEqualTo(1);
        Transaction back = transactionDao.selectById(transaction.getId()).orElseThrow();
        // ...and a restore clears it again.
        assertThat(back.getDeletedByUserId()).isNull();
        assertThat(back.getDeletedByName()).isNull();
        assertThat(transactionDao.countByWalletId(wallet.getId())).isEqualTo(1);
    }

    @Test
    void trashPurgeQueries_seeTrashedRows_andTheForeignKeysTheyStillHold() {
        WalletDao walletDao = new WalletDaoImpl(domaConfig);
        CategoryDao categoryDao = new CategoryDaoImpl(domaConfig);
        TransactionDao transactionDao = new TransactionDaoImpl(domaConfig);
        Long familyId = 500L;
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(40).withNano(0);

        Wallet wallet = insertWallet(walletDao, familyId, "Ví Thùng Rác IT");
        Category category = insertCategory(categoryDao, familyId, "Danh Mục Thùng Rác IT");
        Transaction transaction = new Transaction();
        transaction.setWalletId(wallet.getId());
        transaction.setCategoryId(category.getId());
        transaction.setFamilyId(familyId);
        transaction.setUserId(1L);
        transaction.setType("EXPENSE");
        transaction.setAmount(new BigDecimal("50000.00"));
        transaction.setOccurredAt(LocalDateTime.now().withNano(0));
        transactionDao.insert(transaction);

        transaction.setDeletedAt(deletedAt);
        transactionDao.update(transaction);
        wallet.setDeletedAt(deletedAt);
        walletDao.update(wallet);
        category.setDeletedAt(deletedAt);
        categoryDao.update(category);

        // The trashed transaction no longer counts for "can this wallet be deleted?"... but still holds the FK.
        assertThat(transactionDao.countByWalletId(wallet.getId())).isZero();
        assertThat(transactionDao.countAllByWalletId(wallet.getId())).isEqualTo(1);
        assertThat(transactionDao.countAllByCategoryId(category.getId())).isEqualTo(1);

        assertThat(walletDao.selectDeletedById(wallet.getId())).isPresent();
        assertThat(categoryDao.selectDeletedById(category.getId())).isPresent();
        assertThat(walletDao.selectDeletedByFamilyId(familyId)).extracting(Wallet::getId).containsExactly(wallet.getId());
        assertThat(categoryDao.selectDeletedByFamilyId(familyId)).extracting(Category::getId)
                .containsExactly(category.getId());
        assertThat(transactionDao.selectDeletedByFamilyId(familyId)).extracting(Transaction::getId)
                .containsExactly(transaction.getId());

        // Retention cutoff: in the trash 40 days → picked by a 30-day cutoff, not by a 50-day one.
        LocalDateTime cutoff30 = LocalDateTime.now().minusDays(30);
        LocalDateTime cutoff50 = LocalDateTime.now().minusDays(50);
        assertThat(transactionDao.selectDeletedBefore(cutoff30)).extracting(Transaction::getId).contains(transaction.getId());
        assertThat(walletDao.selectDeletedBefore(cutoff30)).extracting(Wallet::getId).contains(wallet.getId());
        assertThat(categoryDao.selectDeletedBefore(cutoff30)).extracting(Category::getId).contains(category.getId());
        assertThat(transactionDao.selectDeletedBefore(cutoff50)).extracting(Transaction::getId)
                .doesNotContain(transaction.getId());

        // Transaction first, then the wallet/category it pointed at — the order TrashService sweeps in.
        Transaction trashed = transactionDao.selectDeletedById(transaction.getId()).orElseThrow();
        assertThat(transactionDao.delete(trashed)).isEqualTo(1);
        assertThat(walletDao.delete(walletDao.selectDeletedById(wallet.getId()).orElseThrow())).isEqualTo(1);
        assertThat(categoryDao.delete(categoryDao.selectDeletedById(category.getId()).orElseThrow())).isEqualTo(1);
        assertThat(transactionDao.selectDeletedById(transaction.getId())).isEmpty();
        assertThat(walletDao.selectDeletedById(wallet.getId())).isEmpty();
        assertThat(categoryDao.selectDeletedById(category.getId())).isEmpty();
    }

    @Test
    void transactionAuditLog_storesJsonSnapshots_andIsReadBackPerFamilyInOrder() {
        TransactionAuditLogDao auditDao = new TransactionAuditLogDaoImpl(domaConfig);
        Long familyId = 400L;
        Long transactionId = 9001L;

        TransactionAuditLog created = auditLog(familyId, transactionId, "CREATED", null,
                "{\"amount\": 150000.00, \"note\": \"Tiền chợ\"}");
        TransactionAuditLog updated = auditLog(familyId, transactionId, "UPDATED",
                "{\"amount\": 150000.00, \"note\": \"Tiền chợ\"}", "{\"amount\": 175000.00, \"note\": \"Tiền chợ\"}");
        auditDao.insert(created);
        auditDao.insert(updated);
        // Same transaction id, different family — must never show up in family 400's history.
        auditDao.insert(auditLog(401L, transactionId, "CREATED", null, "{\"amount\": 1}"));

        var history = auditDao.selectByTransactionIdAndFamilyId(transactionId, familyId);

        assertThat(history).extracting(TransactionAuditLog::getAction).containsExactly("CREATED", "UPDATED");
        assertThat(history.get(0).getBeforeJson()).isNull();
        // MySQL's JSON column may re-format the text (spacing/key order), so compare content, not the string.
        assertThat(history.get(1).getAfterJson()).contains("175000").contains("Tiền chợ");
        assertThat(history.get(1).getActorName()).isEqualTo("Người Sửa IT");
    }

    private static TransactionAuditLog auditLog(Long familyId, Long transactionId, String action,
                                                String beforeJson, String afterJson) {
        TransactionAuditLog log = new TransactionAuditLog();
        log.setFamilyId(familyId);
        log.setTransactionId(transactionId);
        log.setAction(action);
        log.setActorUserId(7L);
        log.setActorName("Người Sửa IT");
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setCreatedAt(LocalDateTime.now().withNano(0));
        return log;
    }

    @Test
    void walletTransferInsert_sumsAndPaging_workEndToEnd() {
        WalletDao walletDao = new WalletDaoImpl(domaConfig);
        WalletTransferDao transferDao = new WalletTransferDaoImpl(domaConfig);
        Long familyId = 300L;

        Wallet from = insertWallet(walletDao, familyId, "Ví nguồn IT");
        Wallet to = insertWallet(walletDao, familyId, "Ví đích IT");

        WalletTransfer first = insertTransfer(transferDao, familyId, from, to, "100.00", LocalDateTime.of(2026, 1, 1, 8, 0));
        WalletTransfer second = insertTransfer(transferDao, familyId, from, to, "50.50", LocalDateTime.of(2026, 1, 2, 8, 0));

        assertThat(transferDao.sumAmountIntoWallet(to.getId())).isEqualByComparingTo("150.50");
        assertThat(transferDao.sumAmountFromWallet(from.getId())).isEqualByComparingTo("150.50");
        assertThat(transferDao.sumAmountIntoWallet(from.getId())).isEqualByComparingTo("0");
        assertThat(transferDao.countByWalletId(from.getId())).isEqualTo(2);
        assertThat(transferDao.countByFamilyId(familyId)).isEqualTo(2);
        assertThat(transferDao.selectByFamilyIdPaged(familyId, 10, 0))
                .extracting(WalletTransfer::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(transferDao.selectById(first.getId())).isPresent();

        transferDao.delete(first);
        assertThat(transferDao.selectById(first.getId())).isEmpty();
        assertThat(transferDao.sumAmountFromWallet(from.getId())).isEqualByComparingTo("50.50");
    }

    private WalletTransfer insertTransfer(
            WalletTransferDao transferDao, Long familyId, Wallet from, Wallet to, String amount,
            LocalDateTime occurredAt) {
        WalletTransfer transfer = new WalletTransfer();
        transfer.setFamilyId(familyId);
        transfer.setFromWalletId(from.getId());
        transfer.setToWalletId(to.getId());
        transfer.setAmount(new BigDecimal(amount));
        transfer.setOccurredAt(occurredAt);
        transfer.setCreatedByUserId(1L);
        transfer.setCreatedAt(LocalDateTime.now().withNano(0));
        transferDao.insert(transfer);
        return transfer;
    }
}
