package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 80% / 100% budget notifications (README mục 2, 18) and the A6 scopes/rollover — moved here from
 * TransactionServiceTest together with the logic.
 */
@ExtendWith(MockitoExtension.class)
class BudgetMonitorTest {

    private static final Long FAMILY = 1L;
    private static final Long USER = 7L;
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEB_1 = LocalDate.of(2026, 2, 1);

    @Mock
    private BudgetDao budgetDao;
    @Mock
    private TransactionDao transactionDao;
    @Mock
    private CategoryService categoryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BudgetMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new BudgetMonitor(budgetDao, transactionDao, categoryService, eventPublisher);
        Category food = new Category();
        food.setId(5L);
        food.setName("Ăn uống");
        lenient().when(categoryService.requireOwnedByFamily(5L, FAMILY)).thenReturn(food);
        lenient().when(categoryService.childIdsOf(5L, FAMILY)).thenReturn(List.of());
    }

    @Test
    void publishesWarning_whenCategorySpendingCrossesEightyPercent() {
        List<ExpenseEvent> events = record(List.of(budget(5L, "1000000")), "800000", null, "100000");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(ExpenseEvent.BUDGET_WARNING);
        assertThat(events.get(0).categoryId()).isEqualTo(5L);
        assertThat(events.get(0).categoryName()).isEqualTo("Ăn uống");
        assertThat(events.get(0).totalSpent()).isEqualByComparingTo("800000");
        assertThat(events.get(0).limitAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void publishesOnlyExceeded_whenOneExpenseJumpsFromBelowEightyPercentPastTheLimit() {
        assertThat(record(List.of(budget(5L, "1000000")), "1100000", null, "400000"))
                .extracting(ExpenseEvent::eventType).containsExactly(ExpenseEvent.BUDGET_EXCEEDED);
    }

    @Test
    void publishesNothing_whenSpendingWasAlreadyAboveEightyPercent_orStaysBelowIt() {
        assertThat(record(List.of(budget(5L, "1000000")), "900000", null, "50000")).isEmpty();
    }

    @Test
    void publishesWarning_whenSpendingLandsExactlyOnTheLimit() {
        assertThat(record(List.of(budget(5L, "1000000")), "1000000", null, "300000"))
                .extracting(ExpenseEvent::eventType).containsExactly(ExpenseEvent.BUDGET_WARNING);
    }

    @Test
    void overallBudget_hasANullCategory_andTheOverallLabel() {
        List<ExpenseEvent> events = record(List.of(budget(null, "1000000")), null, "1050000", "100000");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(ExpenseEvent.BUDGET_EXCEEDED);
        assertThat(events.get(0).categoryId()).isNull();
        assertThat(events.get(0).categoryName()).isEqualTo("Tổng chi tiêu");
    }

    @Test
    void bothCategoryAndOverallBudgets_canCrossWithTheSameExpense() {
        List<ExpenseEvent> events = record(List.of(budget(5L, "1000000"), budget(null, "5000000")),
                "1050000", "4050000", "100000");

        assertThat(events).extracting(ExpenseEvent::eventType)
                .containsExactly(ExpenseEvent.BUDGET_EXCEEDED, ExpenseEvent.BUDGET_WARNING);
    }

    @Test
    void walletOrMemberScopedBudget_ignoresExpensesOutsideItsScope() {
        Budget otherWallet = budget(null, "100000");
        otherWallet.setWalletId(99L);
        Budget otherMember = budget(null, "100000");
        otherMember.setUserId(42L);
        when(budgetDao.selectApplicable(FAMILY, "2026-01", "2026")).thenReturn(List.of(otherWallet, otherMember));

        monitor.onExpenseRecorded(FAMILY, USER, "a@b.com", "An", expense("100000"),
                List.of(new BudgetMonitor.Line(5L, new BigDecimal("100000"))));

        verify(eventPublisher, never()).publishEvent(any());
        verify(transactionDao, never()).sumExpenseForBudget(any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test
    void splitExpense_countsOnlyItsPartInTheBudgetsCategory() {
        Budget food = budget(5L, "1000000");
        when(budgetDao.selectApplicable(FAMILY, "2026-01", "2026")).thenReturn(List.of(food));
        // 750k before + the 60k food part = 810k → crosses 80%; the 340k household part doesn't count here.
        when(transactionDao.sumExpenseForBudget(FAMILY, false, List.of(5L), null, null, JAN_1, FEB_1))
                .thenReturn(new BigDecimal("810000"));

        monitor.onExpenseRecorded(FAMILY, USER, "a@b.com", "An", expense("400000"), List.of(
                new BudgetMonitor.Line(5L, new BigDecimal("60000")), new BudgetMonitor.Line(6L, new BigDecimal("340000"))));

        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(ExpenseEvent.BUDGET_WARNING);
    }

    @Test
    void rollover_addsWhatThePreviousMonthLeftUnspent_neverANegativeAmount() {
        Budget february = budget(5L, "1000000");
        february.setPeriodMonth("2026-02");
        february.setRollover(true);
        Budget january = budget(5L, "1000000");
        when(budgetDao.selectSameScope(FAMILY, 5L, null, null, "MONTH", "2026-01")).thenReturn(Optional.of(january));
        when(transactionDao.sumExpenseForBudget(FAMILY, false, List.of(5L), null, null, JAN_1, FEB_1))
                .thenReturn(new BigDecimal("700000"));

        assertThat(monitor.effectiveLimit(february, FAMILY)).isEqualByComparingTo("1300000");

        when(transactionDao.sumExpenseForBudget(FAMILY, false, List.of(5L), null, null, JAN_1, FEB_1))
                .thenReturn(new BigDecimal("1200000"));
        assertThat(monitor.effectiveLimit(february, FAMILY)).isEqualByComparingTo("1000000");
    }

    @Test
    void parentCategoryBudget_includesItsSubCategories() {
        Budget food = budget(5L, "1000000");
        when(categoryService.childIdsOf(5L, FAMILY)).thenReturn(List.of(51L, 52L));
        when(budgetDao.selectApplicable(FAMILY, "2026-01", "2026")).thenReturn(List.of(food));
        when(transactionDao.sumExpenseForBudget(FAMILY, false, List.of(5L, 51L, 52L), null, null, JAN_1, FEB_1))
                .thenReturn(new BigDecimal("100000"));

        monitor.onExpenseRecorded(FAMILY, USER, "a@b.com", "An", expense("100000"),
                List.of(new BudgetMonitor.Line(51L, new BigDecimal("100000"))));

        verify(transactionDao).sumExpenseForBudget(FAMILY, false, List.of(5L, 51L, 52L), null, null, JAN_1, FEB_1);
    }

    /** {@code categoryAfter}/{@code overallAfter}: what each budget has spent once the expense is in. */
    private List<ExpenseEvent> record(List<Budget> budgets, String categoryAfter, String overallAfter, String amount) {
        when(budgetDao.selectApplicable(FAMILY, "2026-01", "2026")).thenReturn(budgets);
        if (categoryAfter != null) {
            when(transactionDao.sumExpenseForBudget(FAMILY, false, List.of(5L), null, null, JAN_1, FEB_1))
                    .thenReturn(new BigDecimal(categoryAfter));
        }
        if (overallAfter != null) {
            when(transactionDao.sumExpenseForBudget(FAMILY, true, List.of(), null, null, JAN_1, FEB_1))
                    .thenReturn(new BigDecimal(overallAfter));
        }
        monitor.onExpenseRecorded(FAMILY, USER, "a@b.com", "An", expense(amount),
                List.of(new BudgetMonitor.Line(5L, new BigDecimal(amount))));
        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static Transaction expense(String amount) {
        Transaction t = new Transaction();
        t.setId(100L);
        t.setFamilyId(FAMILY);
        t.setUserId(USER);
        t.setWalletId(3L);
        t.setCategoryId(5L);
        t.setType("EXPENSE");
        t.setAmount(new BigDecimal(amount));
        t.setOccurredAt(LocalDateTime.of(2026, 1, 15, 10, 0));
        return t;
    }

    private static Budget budget(Long categoryId, String limit) {
        Budget budget = new Budget();
        budget.setFamilyId(FAMILY);
        budget.setCategoryId(categoryId);
        budget.setPeriodMonth("2026-01");
        budget.setLimitAmount(new BigDecimal(limit));
        return budget;
    }
}
