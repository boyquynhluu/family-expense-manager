package com.family.expensemanager.expense.dao;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.EntityAuditLog;
import com.family.expensemanager.expense.domain.entity.Loan;
import com.family.expensemanager.expense.domain.entity.LoanPayment;
import com.family.expensemanager.expense.domain.entity.MemberSpendingLimit;
import com.family.expensemanager.expense.domain.entity.RecurringDraft;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.domain.entity.SavingsGoal;
import com.family.expensemanager.expense.domain.entity.Tag;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.TransactionApproval;
import com.family.expensemanager.expense.domain.entity.TransactionSplit;
import com.family.expensemanager.expense.domain.entity.TransactionTag;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.seasar.doma.jdbc.Config;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The SQL of README A3–A6, B3, C1–C7 against a real MySQL with every Flyway migration applied (V18–V26
 * included) — the class of mistake a mocked-DAO unit test can't see.
 */
@Testcontainers
class NewFeatureDaoIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("fem_expense_it2")
            .withUsername("fem_expense")
            .withPassword("test");

    static Config domaConfig;

    @BeforeAll
    static void migrate() {
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

    private final WalletDao walletDao = new WalletDaoImpl(domaConfig);
    private final CategoryDao categoryDao = new CategoryDaoImpl(domaConfig);
    private final TransactionDao transactionDao = new TransactionDaoImpl(domaConfig);

    private Wallet wallet(Long familyId, String name, String type) {
        Wallet w = new Wallet();
        w.setFamilyId(familyId);
        w.setName(name);
        w.setCurrency("VND");
        w.setInitialBalance(BigDecimal.ZERO);
        w.setWalletType(type);
        if ("CREDIT_CARD".equals(type)) {
            w.setCreditLimit(new BigDecimal("5000000"));
            w.setPaymentDueDay(20);
        }
        walletDao.insert(w);
        return w;
    }

    private Category category(Long familyId, String name, Long parentId) {
        Category c = new Category();
        c.setFamilyId(familyId);
        c.setParentId(parentId);
        c.setName(name);
        c.setType("EXPENSE");
        categoryDao.insert(c);
        return c;
    }

    private Transaction expense(Long familyId, Wallet w, Category c, String amount, LocalDateTime at, Long userId) {
        Transaction t = new Transaction();
        t.setWalletId(w.getId());
        t.setCategoryId(c.getId());
        t.setFamilyId(familyId);
        t.setUserId(userId);
        t.setType("EXPENSE");
        t.setAmount(new BigDecimal(amount));
        t.setOccurredAt(at);
        transactionDao.insert(t);
        return t;
    }

    @Test
    void walletTypes_roundTrip_andTheCreditCardAndFamilyQueries() {
        Wallet card = wallet(1001L, "Thẻ", "CREDIT_CARD");
        wallet(1001L, "Tiền mặt", "CASH");

        Wallet read = walletDao.selectById(card.getId()).orElseThrow();
        assertThat(read.getWalletType()).isEqualTo("CREDIT_CARD");
        assertThat(read.getCreditLimit()).isEqualByComparingTo("5000000");
        assertThat(read.getPaymentDueDay()).isEqualTo(20);
        assertThat(walletDao.selectCreditCardsWithDueDay()).extracting(Wallet::getId).contains(card.getId());
        assertThat(walletDao.selectActiveFamilyIds()).contains(1001L);
    }

    @Test
    void budgets_applicableAndSameScope_andSpendReadsSplitsAndSubCategories() {
        Long family = 1002L;
        Wallet w = wallet(family, "Ví", "CASH");
        Category food = category(family, "Ăn uống", null);
        Category coffee = category(family, "Cà phê", food.getId());
        Category home = category(family, "Nhà cửa", null);
        LocalDateTime jan = LocalDateTime.of(2026, 1, 10, 9, 0);
        expense(family, w, coffee, "30000", jan, 7L);
        Transaction market = expense(family, w, food, "100000", jan, 8L);
        TransactionSplitDao splitDao = new TransactionSplitDaoImpl(domaConfig);
        splitDao.insertAll(List.of(split(market, food, "40000"), split(market, home, "60000")));

        BudgetDao budgetDao = new BudgetDaoImpl(domaConfig);
        Budget monthly = budget(family, food.getId(), null, "MONTH", "2026-01");
        Budget yearly = budget(family, null, w.getId(), "YEAR", "2026");
        budgetDao.insert(monthly);
        budgetDao.insert(yearly);

        assertThat(budgetDao.selectApplicable(family, "2026-01", "2026")).extracting(Budget::getId)
                .containsExactlyInAnyOrder(monthly.getId(), yearly.getId());
        assertThat(budgetDao.selectSameScope(family, food.getId(), null, null, "MONTH", "2026-01")).isPresent();
        assertThat(budgetDao.selectSameScope(family, null, w.getId(), null, "YEAR", "2026")).isPresent();
        assertThat(budgetDao.selectSameScope(family, null, null, null, "YEAR", "2026")).isEmpty();
        assertThat(budgetDao.countByWalletId(w.getId())).isEqualTo(1);

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        // Food + its child coffee: 30k coffee + the 40k food part of the split market receipt.
        assertThat(transactionDao.sumExpenseForBudget(family, false, List.of(food.getId(), coffee.getId()), null, null, from, to))
                .isEqualByComparingTo("70000");
        assertThat(transactionDao.sumExpenseForBudget(family, true, List.of(), null, null, from, to)).isEqualByComparingTo("130000");
        assertThat(transactionDao.sumExpenseForBudget(family, true, List.of(), null, 8L, from, to)).isEqualByComparingTo("100000");
        assertThat(categoryDao.selectChildIds(food.getId())).containsExactly(coffee.getId());
        assertThat(categoryDao.countActiveChildren(food.getId())).isEqualTo(1);
        // The per-category report now reads category lines.
        assertThat(transactionDao.sumAmountByCategoryPeriodAndType(family, home.getId(), "2026-01", "EXPENSE"))
                .isEqualByComparingTo("60000");
        assertThat(splitDao.selectByTransactionIds(List.of(market.getId()))).hasSize(2);
        assertThat(splitDao.countByCategoryId(home.getId())).isEqualTo(1);
        assertThat(transactionDao.selectByFamilyIdFiltered(family, 7L, false, null, food.getId(), null, null, null,
                null, null, null, null, 10, 0)).hasSize(2);

        splitDao.deleteByTransactionId(market.getId());
        assertThat(splitDao.selectByTransactionIds(List.of(market.getId()))).isEmpty();
    }

    @Test
    void refunds_areNegativeExpenses_thatNetOutTheirOriginal() {
        Long family = 1003L;
        Wallet w = wallet(family, "Ví", "CASH");
        Category shop = category(family, "Mua sắm", null);
        LocalDateTime at = LocalDateTime.of(2026, 3, 1, 9, 0);
        Transaction original = expense(family, w, shop, "500000", at, 7L);
        Transaction refund = new Transaction();
        refund.setWalletId(w.getId());
        refund.setCategoryId(shop.getId());
        refund.setRefundOfId(original.getId());
        refund.setFamilyId(family);
        refund.setUserId(7L);
        refund.setType("EXPENSE");
        refund.setAmount(new BigDecimal("-200000"));
        refund.setOccurredAt(at.plusDays(2));
        transactionDao.insert(refund);

        assertThat(transactionDao.sumRefundedOf(original.getId())).isEqualByComparingTo("200000");
        assertThat(transactionDao.countActiveRefundsOf(original.getId())).isEqualTo(1);
        assertThat(transactionDao.sumAmountByCategoryPeriodAndType(family, shop.getId(), "2026-03", "EXPENSE"))
                .isEqualByComparingTo("300000");
        assertThat(transactionDao.selectById(refund.getId()).orElseThrow().getRefundOfId()).isEqualTo(original.getId());

        // The CHECK keeps a negative amount for refunds only.
        Transaction bad = new Transaction();
        bad.setWalletId(w.getId());
        bad.setCategoryId(shop.getId());
        bad.setFamilyId(family);
        bad.setUserId(7L);
        bad.setType("EXPENSE");
        bad.setAmount(new BigDecimal("-1"));
        bad.setOccurredAt(at);
        assertThatThrownBy(() -> transactionDao.insert(bad)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tags_linkFilterAndReport() {
        Long family = 1004L;
        Wallet w = wallet(family, "Ví", "CASH");
        Category food = category(family, "Ăn", null);
        Transaction trip = expense(family, w, food, "250000", LocalDateTime.of(2026, 4, 2, 9, 0), 7L);
        expense(family, w, food, "50000", LocalDateTime.of(2026, 4, 3, 9, 0), 7L);
        TagDao tagDao = new TagDaoImpl(domaConfig);
        Tag dalat = new Tag();
        dalat.setFamilyId(family);
        dalat.setName("Du lịch Đà Lạt");
        dalat.setCreatedAt(LocalDateTime.now().withNano(0));
        tagDao.insert(dalat);
        TransactionTag link = new TransactionTag();
        link.setTransactionId(trip.getId());
        link.setTagId(dalat.getId());
        tagDao.insertLinks(List.of(link));

        assertThat(tagDao.selectByFamilyAndName(family, "Du lịch Đà Lạt")).isPresent();
        List<Map<String, Object>> names = tagDao.selectNamesByTransactionIds(List.of(trip.getId()));
        assertThat(names).hasSize(1);
        assertThat(names.get(0).get("name")).isEqualTo("Du lịch Đà Lạt");
        assertThat(transactionDao.countByFamilyIdFiltered(family, 7L, false, null, null, null, null, null, null, null,
                null, dalat.getId())).isEqualTo(1);
        List<Map<String, Object>> report = new ReportDaoImpl(domaConfig)
                .sumByTagAndType(family, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1));
        assertThat(report).hasSize(1);
        assertThat(new BigDecimal(report.get(0).get("total").toString())).isEqualByComparingTo("250000");
        tagDao.deleteLinksByTransactionId(trip.getId());
        assertThat(tagDao.selectTransactionIdsByTagId(dalat.getId())).isEmpty();
    }

    @Test
    void loans_moveWalletBalances_andFeedTheReportAndReminders() {
        Long family = 1005L;
        Wallet w = wallet(family, "Ví", "CASH");
        LoanDao loanDao = new LoanDaoImpl(domaConfig);
        LoanPaymentDao paymentDao = new LoanPaymentDaoImpl(domaConfig);
        Loan borrowed = loan(family, w, "BORROWED", "3000000", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20));
        Loan lent = loan(family, w, "LENT", "1000000", LocalDate.of(2026, 5, 2), null);
        loanDao.insert(borrowed);
        loanDao.insert(lent);
        paymentDao.insert(payment(borrowed, w, "500000", LocalDateTime.of(2026, 5, 10, 9, 0)));
        paymentDao.insert(payment(lent, w, "200000", LocalDateTime.of(2026, 6, 1, 9, 0)));

        // +3,000,000 borrowed - 1,000,000 lent - 500,000 repaid + 200,000 collected
        assertThat(loanDao.sumNetFlowForWallet(w.getId())).isEqualByComparingTo("1700000");
        assertThat(paymentDao.sumByLoanId(borrowed.getId())).isEqualByComparingTo("500000");
        assertThat(loanDao.countByWalletId(w.getId())).isEqualTo(4);
        assertThat(loanDao.countByFamily(family, null, "OPEN")).isEqualTo(2);
        assertThat(loanDao.countByFamily(family, 7L, null)).isEqualTo(1);
        assertThat(loanDao.countByFamily(family, 99L, null)).isZero();
        assertThat(loanDao.selectByFamilyPaged(family, null, null, 10, 0)).hasSize(2);
        assertThat(loanDao.selectByFamilyPaged(family, 8L, null, 10, 0)).extracting(Loan::getId).containsExactly(lent.getId());
        List<Map<String, Object>> may = new ReportDaoImpl(domaConfig)
                .sumLoanFlowsByWallet(family, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));
        assertThat(new BigDecimal(may.get(0).get("total").toString())).isEqualByComparingTo("1500000");
        assertThat(loanDao.selectDueForReminder(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 21)))
                .extracting(Loan::getId).contains(borrowed.getId());
        borrowed.setLastRemindedFor(borrowed.getDueDate());
        loanDao.update(borrowed);
        assertThat(loanDao.selectDueForReminder(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 21)))
                .extracting(Loan::getId).doesNotContain(borrowed.getId());
    }

    @Test
    void recurringReminders_drafts_approvals_limits_goals_audit_andSummaryRuns() {
        Long family = 1006L;
        Wallet w = wallet(family, "Ví", "CASH");
        Category bills = category(family, "Hoá đơn", null);

        RecurringTransactionDao ruleDao = new RecurringTransactionDaoImpl(domaConfig);
        RecurringTransaction rule = new RecurringTransaction();
        rule.setFamilyId(family);
        rule.setWalletId(w.getId());
        rule.setCategoryId(bills.getId());
        rule.setCreatedByUserId(7L);
        rule.setCreatedByEmail("a@b.com");
        rule.setCreatedByDisplayName("An");
        rule.setType("EXPENSE");
        rule.setAmount(new BigDecimal("300000"));
        rule.setDayOfMonth(10);
        rule.setFrequency("MONTHLY");
        rule.setMode("CONFIRM");
        rule.setRemindDaysBefore(3);
        rule.setStartDate(LocalDate.of(2026, 1, 10));
        rule.setNextRunDate(LocalDate.of(2026, 10, 10));
        rule.setActive(true);
        rule.setCreatedAt(LocalDateTime.now().withNano(0));
        ruleDao.insert(rule);
        assertThat(ruleDao.selectReminderDue(LocalDate.of(2026, 10, 7))).extracting(RecurringTransaction::getId)
                .contains(rule.getId());
        assertThat(ruleDao.selectReminderDue(LocalDate.of(2026, 10, 6))).extracting(RecurringTransaction::getId)
                .doesNotContain(rule.getId());
        assertThat(ruleDao.selectById(rule.getId()).orElseThrow().getMode()).isEqualTo("CONFIRM");

        RecurringDraftDao draftDao = new RecurringDraftDaoImpl(domaConfig);
        RecurringDraft draft = new RecurringDraft();
        draft.setFamilyId(family);
        draft.setRecurringId(rule.getId());
        draft.setWalletId(w.getId());
        draft.setCategoryId(bills.getId());
        draft.setType("EXPENSE");
        draft.setSuggestedAmount(new BigDecimal("300000"));
        draft.setDueDate(LocalDate.of(2026, 10, 10));
        draft.setCreatedByUserId(7L);
        draft.setStatus("PENDING");
        draft.setCreatedAt(LocalDateTime.now().withNano(0));
        draftDao.insert(draft);
        assertThat(draftDao.countPendingByFamilyId(family)).isEqualTo(1);
        assertThat(draftDao.selectByRecurringAndDueDate(rule.getId(), LocalDate.of(2026, 10, 10))).isPresent();
        assertThat(draftDao.selectPendingByFamilyIdPaged(family, 10, 0)).hasSize(1);

        TransactionApprovalDao approvalDao = new TransactionApprovalDaoImpl(domaConfig);
        TransactionApproval approval = new TransactionApproval();
        approval.setFamilyId(family);
        approval.setRequesterUserId(8L);
        approval.setWalletId(w.getId());
        approval.setCategoryId(bills.getId());
        approval.setAmount(new BigDecimal("2000000"));
        approval.setOccurredAt(LocalDateTime.of(2026, 10, 5, 9, 0));
        approval.setStatus("PENDING");
        approval.setCreatedAt(LocalDateTime.now().withNano(0));
        approvalDao.insert(approval);
        assertThat(approvalDao.countByFamily(family, null)).isEqualTo(1);
        assertThat(approvalDao.countByFamily(family, 9L)).isZero();
        assertThat(approvalDao.selectByFamilyPaged(family, 8L, 10, 0)).hasSize(1);
        assertThat(approvalDao.countPendingByFamilyId(family)).isEqualTo(1);

        MemberSpendingLimitDao limitDao = new MemberSpendingLimitDaoImpl(domaConfig);
        MemberSpendingLimit limit = new MemberSpendingLimit();
        limit.setFamilyId(family);
        limit.setUserId(8L);
        limit.setDailyLimit(new BigDecimal("100000"));
        limit.setUpdatedAt(LocalDateTime.now().withNano(0));
        limitDao.insert(limit);
        assertThat(limitDao.selectByFamilyAndUser(family, 8L)).isPresent();
        Transaction spent = expense(family, w, bills, "60000", LocalDateTime.of(2026, 10, 5, 8, 0), 8L);
        assertThat(transactionDao.sumUserExpenseBetween(family, 8L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), null))
                .isEqualByComparingTo("60000");
        assertThat(transactionDao.sumUserExpenseBetween(family, 8L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                spent.getId())).isEqualByComparingTo("0");

        SavingsGoalDao goalDao = new SavingsGoalDaoImpl(domaConfig);
        SavingsGoal goal = new SavingsGoal();
        goal.setFamilyId(family);
        goal.setName("Mua xe");
        goal.setTargetAmount(new BigDecimal("10000000"));
        goal.setWalletId(w.getId());
        goal.setCreatedByUserId(7L);
        goal.setCreatedAt(LocalDateTime.now().withNano(0));
        goalDao.insert(goal);
        assertThat(goalDao.selectInProgress()).extracting(SavingsGoal::getId).contains(goal.getId());
        assertThat(goalDao.countByWalletId(w.getId())).isEqualTo(1);

        EntityAuditLogDao auditDao = new EntityAuditLogDaoImpl(domaConfig);
        EntityAuditLog entry = new EntityAuditLog();
        entry.setFamilyId(family);
        entry.setEntityType("WALLET");
        entry.setEntityId(w.getId());
        entry.setAction("UPDATED");
        entry.setBeforeJson("{\"name\": \"Ví\"}");
        entry.setAfterJson("{\"name\": \"Ví mới\"}");
        entry.setCreatedAt(LocalDateTime.now().withNano(0));
        auditDao.insert(entry);
        assertThat(auditDao.selectByEntity(family, "WALLET", w.getId())).hasSize(1);

        MonthlySummaryRunDao runDao = new MonthlySummaryRunDaoImpl(domaConfig);
        com.family.expensemanager.expense.domain.entity.MonthlySummaryRun run =
                new com.family.expensemanager.expense.domain.entity.MonthlySummaryRun();
        run.setFamilyId(family);
        run.setPeriodMonth("2026-09");
        run.setSentAt(LocalDateTime.now().withNano(0));
        runDao.insert(run);
        assertThat(runDao.selectByFamilyAndMonth(family, "2026-09")).isPresent();

        FamilySettingDao settingDao = new FamilySettingDaoImpl(domaConfig);
        com.family.expensemanager.expense.domain.entity.FamilySetting setting =
                new com.family.expensemanager.expense.domain.entity.FamilySetting();
        setting.setFamilyId(family);
        setting.setApprovalThreshold(new BigDecimal("1000000"));
        setting.setUpdatedAt(LocalDateTime.now().withNano(0));
        settingDao.insert(setting);
        assertThat(settingDao.selectByFamilyId(family).orElseThrow().getApprovalThreshold()).isEqualByComparingTo("1000000");
    }

    @Test
    void familyLoan_movesMoneyBetweenTheTwoWallets_andTheFamilyTotalStaysTheSame() {
        Long family = 1007L;
        Wallet dad = wallet(family, "Ví bố", "CASH");
        Wallet mom = wallet(family, "Ví mẹ", "CASH");
        LoanDao loanDao = new LoanDaoImpl(domaConfig);
        LoanPaymentDao paymentDao = new LoanPaymentDaoImpl(domaConfig);
        // Dad borrows 2,000,000 from mom's wallet, then repays 500,000.
        Loan loan = loan(family, dad, "BORROWED", "2000000", LocalDate.of(2026, 7, 1), null);
        loan.setCounterpartyWalletId(mom.getId());
        loanDao.insert(loan);
        paymentDao.insert(payment(loan, dad, "500000", LocalDateTime.of(2026, 7, 15, 9, 0)));

        assertThat(loanDao.sumNetFlowForWallet(dad.getId())).isEqualByComparingTo("1500000");
        assertThat(loanDao.sumNetFlowForWallet(mom.getId())).isEqualByComparingTo("-1500000");
        assertThat(loanDao.countByWalletId(mom.getId())).isEqualTo(1);
        assertThat(loanDao.selectById(loan.getId()).orElseThrow().getCounterpartyWalletId()).isEqualTo(mom.getId());
        List<Map<String, Object>> july = new ReportDaoImpl(domaConfig)
                .sumLoanFlowsByWallet(family, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        BigDecimal total = july.stream().map(r -> new BigDecimal(r.get("total").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(july).hasSize(2);
        assertThat(total).isEqualByComparingTo("0");
    }

    private static TransactionSplit split(Transaction t, Category c, String amount) {
        TransactionSplit s = new TransactionSplit();
        s.setTransactionId(t.getId());
        s.setCategoryId(c.getId());
        s.setAmount(new BigDecimal(amount));
        return s;
    }

    private static Budget budget(Long family, Long categoryId, Long walletId, String type, String period) {
        Budget b = new Budget();
        b.setFamilyId(family);
        b.setCategoryId(categoryId);
        b.setWalletId(walletId);
        b.setPeriodType(type);
        b.setPeriodMonth(period);
        b.setLimitAmount(new BigDecimal("1000000"));
        b.setRollover(true);
        return b;
    }

    private static Loan loan(Long family, Wallet w, String direction, String principal, LocalDate start, LocalDate due) {
        Loan l = new Loan();
        l.setFamilyId(family);
        l.setMemberUserId("BORROWED".equals(direction) ? 7L : 8L);
        l.setDirection(direction);
        l.setCounterpartyName("Anh Hùng");
        l.setPrincipal(new BigDecimal(principal));
        l.setWalletId(w.getId());
        l.setStartDate(start);
        l.setDueDate(due);
        l.setStatus("OPEN");
        l.setCreatedByUserId(7L);
        l.setCreatedAt(LocalDateTime.now().withNano(0));
        return l;
    }

    private static LoanPayment payment(Loan loan, Wallet w, String amount, LocalDateTime at) {
        LoanPayment p = new LoanPayment();
        p.setLoanId(loan.getId());
        p.setFamilyId(loan.getFamilyId());
        p.setWalletId(w.getId());
        p.setAmount(new BigDecimal(amount));
        p.setPaidAt(at);
        p.setCreatedByUserId(7L);
        p.setCreatedAt(LocalDateTime.now().withNano(0));
        return p;
    }
}
