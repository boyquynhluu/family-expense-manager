package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.dto.CreateRecurringTransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    @Mock
    private RecurringTransactionDao recurringTransactionDao;
    @Mock
    private WalletService walletService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TransactionService transactionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static final Long OTHER_USER_ID = 99L;

    private RecurringTransactionService service;

    private static Clock clockOn(String isoDate) {
        return Clock.fixed(LocalDate.parse(isoDate).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionService(
                recurringTransactionDao, walletService, categoryService, transactionService, clockOn("2026-01-15"),
                eventPublisher);
    }

    @Test
    void create_setsNextRunDate_toStartDateItself_whenDayOfMonthMatchesStartDate() {
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.valueOf(5000000), "Tiền nhà", null, 1, null, null, LocalDate.of(2026, 2, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        verify(walletService).requireOwnedByFamily(5L, 1L);
        verify(categoryService).requireOwnedByFamily(7L, 1L, "EXPENSE");
    }

    @Test
    void create_setsNextRunDate_toNextMonth_whenDayOfMonthAlreadyPassedInStartMonth() {
        // startDate is the 15th but the bill runs on the 1st — the 1st of the start
        // month already passed, so the first real occurrence is next month's 1st.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 1, null, null, LocalDate.of(2026, 2, 15), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void create_floorsNextRunDateToToday_whenStartDateIsMonthsInThePast() {
        // Clock is fixed at 2026-01-15 (see setUp). A rule created today with a start
        // date months ago must never backfill — its first run should be the next real
        // occurrence from today, not a catch-up burst the moment the scheduler next runs.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.valueOf(5000000), "Tiền nhà", null, 1, null, null, LocalDate.of(2025, 10, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void create_startsThisMonth_whenPastStartDateButDayOfMonthHasNotPassedYet() {
        // Same fixed "today" (Jan 15) but dayOfMonth (20) hasn't happened yet this month
        // — the rule should still be allowed to fire later this month, not skip to next.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 20, null, null, LocalDate.of(2025, 6, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void update_floorsNextRunDateToToday_whenStartDateIsInThePast() {
        RecurringTransaction r = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(r));
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 1, null, null, LocalDate.of(2025, 10, 1), null);

        var response = service.update(9L, 1L, 10L, false, request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void create_clampsDayOfMonth_toLastDayOfShorterMonth() {
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 31, null, null, LocalDate.of(2026, 2, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void generateDueTransactions_createsOneTransaction_andAdvancesToNextMonth() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        assertThat(rule.getLastRunDate()).isNull(); // never fired yet — frontend shows "Chưa thực hiện"
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN,
                        LocalDate.of(2026, 1, 1).atStartOfDay(), null, false, null));

        service.generateDueTransactions();

        verify(transactionService, times(1)).create(eq(1L), eq(10L), eq("a@b.com"), eq("An"), any());
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        // Now that it fired once, frontend should show "Hoàn thành" for this rule.
        assertThat(rule.getLastRunDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        verify(recurringTransactionDao).update(rule);
    }

    @Test
    void generateDueTransactions_catchesUpAllMissedMonths_inOneRun() {
        // Server was "down" since October — nextRunDate is 3 months behind today (Jan 15).
        RecurringTransaction rule = rule(1L, LocalDate.of(2025, 10, 1), 1, null);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN, null, null, false, null));

        service.generateDueTransactions();

        // Oct, Nov, Dec, Jan = 4 occurrences due by Jan 15.
        verify(transactionService, times(4)).create(eq(1L), eq(10L), eq("a@b.com"), eq("An"), any());
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(rule.getLastRunDate()).isEqualTo(LocalDate.of(2026, 1, 1)); // the most recent occurrence processed
    }

    @Test
    void generateDueTransactions_deactivatesRule_onceNextRunDatePassesEndDate() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, LocalDate.of(2026, 1, 31));
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN, null, null, false, null));

        service.generateDueTransactions();

        assertThat(rule.getActive()).isFalse();
    }

    @Test
    void generateDueTransactions_skipsFailingRule_andStillProcessesOthers() {
        RecurringTransaction failing = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        RecurringTransaction ok = rule(2L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(failing, ok));
        when(transactionService.create(eq(1L), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("wallet was deleted"));
        when(transactionService.create(eq(2L), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 2L, 10L, null, "EXPENSE", BigDecimal.TEN, null, null, false, null));

        service.generateDueTransactions();

        verify(recurringTransactionDao, never()).update(failing);
        verify(recurringTransactionDao).update(ok);
    }

    @Test
    void generateDueTransactions_publishesRecurringExecutedEvent_afterCreatingTransaction() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        rule.setNote("Tiền nhà");
        Category category = new Category();
        category.setName("Nhà ở");
        when(categoryService.requireOwnedByFamily(7L, 1L)).thenReturn(category);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN,
                        LocalDate.of(2026, 1, 1).atStartOfDay(), null, false, null));

        service.generateDueTransactions();

        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ExpenseEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.RECURRING_EXECUTED);
        assertThat(event.familyId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.transactionId()).isEqualTo(99L);
        assertThat(event.categoryName()).isEqualTo("Nhà ở");
        assertThat(event.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(event.occurredOn()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(event.note()).isEqualTo("Tiền nhà");
    }

    @Test
    void generateDueTransactions_publishesRecurringFailedEvent_whenRuleThrows() {
        RecurringTransaction failing = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(failing));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("wallet was deleted"));

        service.generateDueTransactions();

        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ExpenseEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.RECURRING_FAILED);
        assertThat(event.transactionId()).isNull();
        assertThat(event.occurredOn()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void generateDueTransactions_keepsProcessing_whenPublishingEventThrows() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN, null, null, false, null));
        doThrow(new IllegalStateException("kafka down")).when(eventPublisher).publishEvent(any(ExpenseEvent.class));

        service.generateDueTransactions();

        verify(recurringTransactionDao).update(rule);
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void update_throwsForbidden_whenMemberEditsSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 1, null, null, LocalDate.of(2026, 2, 1), null);

        assertForbidden(() -> service.update(9L, 1L, OTHER_USER_ID, false, request));
        verify(recurringTransactionDao, never()).update(any());
    }

    @Test
    void update_succeeds_whenOwnerEditsSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, null, 1, null, null, LocalDate.of(2026, 2, 1), null);

        service.update(9L, 1L, OTHER_USER_ID, true, request);

        verify(recurringTransactionDao).update(rule);
    }

    @Test
    void setActive_throwsForbidden_whenMemberTogglesSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        assertForbidden(() -> service.setActive(9L, 1L, OTHER_USER_ID, false, false));
        assertThat(rule.getActive()).isTrue();
        verify(recurringTransactionDao, never()).update(any());
    }

    @Test
    void setActive_succeeds_whenOwnerTogglesSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.setActive(9L, 1L, OTHER_USER_ID, true, false);

        assertThat(rule.getActive()).isFalse();
        verify(recurringTransactionDao).update(rule);
    }

    @Test
    void delete_throwsForbidden_whenMemberDeletesSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        assertForbidden(() -> service.delete(9L, 1L, OTHER_USER_ID, false));
        verify(recurringTransactionDao, never()).delete(any());
    }

    @Test
    void delete_succeeds_whenOwnerDeletesSomeoneElsesRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.delete(9L, 1L, OTHER_USER_ID, true);

        verify(recurringTransactionDao).delete(rule);
    }

    @Test
    void setActive_updatesFlag_onOwnedRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.setActive(9L, 1L, 10L, false, false);

        assertThat(rule.getActive()).isFalse();
        verify(recurringTransactionDao).update(rule);
    }

    @Test
    void delete_removesOwnedRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.delete(9L, 1L, 10L, false);

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(recurringTransactionDao).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(rule);
    }

    @Test
    void listByFamilyPaged_returnsPageWithOffset() {
        when(recurringTransactionDao.countByFamilyId(1L)).thenReturn(12L);
        when(recurringTransactionDao.selectByFamilyIdPaged(1L, 5, 10)).thenReturn(List.of(
                rule(1L, LocalDate.of(2026, 1, 1), 1, null), rule(1L, LocalDate.of(2026, 2, 1), 1, null)));

        var result = service.listByFamilyPaged(1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listByFamilyPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> service.listByFamilyPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> service.listByFamilyPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.listByFamilyPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_defaultsFrequencyToMonthly_whenRequestOmitsIt() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request(null, 5, null, null, LocalDate.of(2026, 2, 1)));

        assertThat(response.frequency()).isEqualTo("MONTHLY");
        assertThat(response.dayOfWeek()).isNull();
        assertThat(response.monthOfYear()).isNull();
    }

    @Test
    void create_weekly_startsOnChosenWeekday_onOrAfterStartDate() {
        // 2026-01-15 is a Thursday (see setUp clock); Monday=1 next falls on Jan 19.
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("WEEKLY", null, 1, null, LocalDate.of(2026, 1, 15)));

        assertThat(response.frequency()).isEqualTo("WEEKLY");
        assertThat(response.dayOfWeek()).isEqualTo(1);
        assertThat(response.dayOfMonth()).isEqualTo(1);
        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    }

    @Test
    void create_weekly_runsToday_whenStartDateIsTheChosenWeekday() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("WEEKLY", null, 4, null, LocalDate.of(2026, 1, 15)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void create_weekly_usesFutureStartDate() {
        // 2026-02-01 is a Sunday; the first Wednesday on or after it is Feb 4.
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("WEEKLY", null, 3, null, LocalDate.of(2026, 2, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 4));
    }

    @Test
    void create_weekly_floorsPastStartDateToToday() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("WEEKLY", null, 1, null, LocalDate.of(2025, 10, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    }

    @Test
    void create_yearly_usesStartYear_whenOccurrenceNotYetPassed() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("YEARLY", 5, null, 3, LocalDate.of(2026, 1, 1)));

        assertThat(response.frequency()).isEqualTo("YEARLY");
        assertThat(response.monthOfYear()).isEqualTo(3);
        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void create_yearly_rollsToNextYear_whenOccurrenceAlreadyPassed() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("YEARLY", 10, null, 1, LocalDate.of(2026, 1, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void create_yearly_floorsPastStartDateToToday() {
        // Jan 20 has not passed yet on the fixed "today" (Jan 15), so a years-old start date must not backfill.
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("YEARLY", 20, null, 1, LocalDate.of(2020, 1, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void create_yearly_clampsFeb29ToFeb28_inNonLeapYear() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("YEARLY", 29, null, 2, LocalDate.of(2027, 2, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void create_yearly_keepsFeb29_inLeapYear() {
        var response = service.create(1L, 10L, "a@b.com", "An",
                request("YEARLY", 29, null, 2, LocalDate.of(2027, 6, 1)));

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void create_rejectsWeeklyWithoutDayOfWeek() {
        assertBadRequest(request("WEEKLY", 5, null, null, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void create_rejectsYearlyWithoutMonth() {
        assertBadRequest(request("YEARLY", 5, null, null, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void create_rejectsYearlyWithoutDayOfMonth() {
        assertBadRequest(request("YEARLY", null, null, 3, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void create_rejectsYearlyWithDayBeyondMonthLength() {
        assertBadRequest(request("YEARLY", 30, null, 2, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void create_rejectsMonthlyWithoutDayOfMonth() {
        assertBadRequest(request("MONTHLY", null, 3, null, LocalDate.of(2026, 2, 1)));
        assertBadRequest(request(null, null, 3, null, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void create_rejectsUnknownFrequency() {
        assertBadRequest(request("DAILY", 5, 3, 3, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void update_switchesWeeklyRuleToMonthly_andClearsWeekdayField() {
        RecurringTransaction rule = weeklyRule(LocalDate.of(2026, 1, 19), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        var response = service.update(9L, 1L, 10L, false, request("MONTHLY", 20, null, null, LocalDate.of(2026, 1, 1)));

        assertThat(response.frequency()).isEqualTo("MONTHLY");
        assertThat(response.dayOfWeek()).isNull();
        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void generateDueTransactions_weekly_catchesUpThreeMissedWeeks() {
        // Server "down" for 3 weeks: Mondays Dec 29, Jan 5 and Jan 12 are due by Thursday Jan 15.
        RecurringTransaction rule = weeklyRule(LocalDate.of(2025, 12, 29), 1, null);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        stubTransactionCreate();

        service.generateDueTransactions();

        verify(transactionService, times(3)).create(eq(1L), eq(10L), eq("a@b.com"), eq("An"), any());
        assertThat(rule.getLastRunDate()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    }

    @Test
    void generateDueTransactions_weekly_deactivatesRule_onceNextRunDatePassesEndDate() {
        RecurringTransaction rule = weeklyRule(LocalDate.of(2026, 1, 12), 1, LocalDate.of(2026, 1, 15));
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        stubTransactionCreate();

        service.generateDueTransactions();

        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 1, 19));
        assertThat(rule.getActive()).isFalse();
    }

    @Test
    void generateDueTransactions_yearly_clampsFeb29ToFeb28InNonLeapYears() {
        // Due runs: 2024-02-29 (leap) and 2025-02-28 (clamped); next is 2026-02-28, not yet due.
        RecurringTransaction rule = yearlyRule(LocalDate.of(2024, 2, 29), 2, 29);
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        stubTransactionCreate();

        service.generateDueTransactions();

        verify(transactionService, times(2)).create(eq(1L), eq(10L), eq("a@b.com"), eq("An"), any());
        assertThat(rule.getLastRunDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void generateDueTransactions_yearly_advancesToNextLeapFeb29() {
        RecurringTransaction rule = yearlyRule(LocalDate.of(2027, 2, 28), 2, 29);
        // Re-anchor the clock past the 2027 run so the loop advances into 2028.
        service = new RecurringTransactionService(
                recurringTransactionDao, walletService, categoryService, transactionService, clockOn("2027-03-01"),
                eventPublisher);
        when(recurringTransactionDao.selectDue(LocalDate.of(2027, 3, 1))).thenReturn(List.of(rule));
        stubTransactionCreate();

        service.generateDueTransactions();

        verify(transactionService, times(1)).create(eq(1L), eq(10L), eq("a@b.com"), eq("An"), any());
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    private void stubTransactionCreate() {
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, null, "EXPENSE", BigDecimal.TEN, null, null,
                        false, null));
    }

    private void assertBadRequest(CreateRecurringTransactionRequest request) {
        assertThatThrownBy(() -> service.create(1L, 10L, "a@b.com", "An", request))
                .isInstanceOf(BadRequestException.class);
        verify(recurringTransactionDao, never()).insert(any());
    }

    private static CreateRecurringTransactionRequest request(
            String frequency, Integer dayOfMonth, Integer dayOfWeek, Integer monthOfYear, LocalDate startDate) {
        return new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, frequency, dayOfMonth, dayOfWeek, monthOfYear, startDate,
                null);
    }

    private static RecurringTransaction weeklyRule(LocalDate nextRunDate, int dayOfWeek, LocalDate endDate) {
        RecurringTransaction r = rule(1L, nextRunDate, 1, endDate);
        r.setFrequency("WEEKLY");
        r.setDayOfWeek(dayOfWeek);
        return r;
    }

    private static RecurringTransaction yearlyRule(LocalDate nextRunDate, int monthOfYear, int dayOfMonth) {
        RecurringTransaction r = rule(1L, nextRunDate, dayOfMonth, null);
        r.setFrequency("YEARLY");
        r.setMonthOfYear(monthOfYear);
        return r;
    }

    private static void assertForbidden(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static RecurringTransaction rule(Long familyId, LocalDate nextRunDate, int dayOfMonth, LocalDate endDate) {
        RecurringTransaction r = new RecurringTransaction();
        r.setId(1L);
        r.setFamilyId(familyId);
        r.setWalletId(5L);
        r.setCategoryId(7L);
        r.setCreatedByUserId(10L);
        r.setCreatedByEmail("a@b.com");
        r.setCreatedByDisplayName("An");
        r.setType("EXPENSE");
        r.setAmount(BigDecimal.TEN);
        r.setFrequency("MONTHLY");
        r.setDayOfMonth(dayOfMonth);
        r.setStartDate(nextRunDate);
        r.setEndDate(endDate);
        r.setNextRunDate(nextRunDate);
        r.setActive(true);
        r.setCreatedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime());
        return r;
    }
}
