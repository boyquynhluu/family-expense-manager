package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.dto.CreateRecurringTransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private RecurringTransactionService service;

    private static Clock clockOn(String isoDate) {
        return Clock.fixed(LocalDate.parse(isoDate).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionService(
                recurringTransactionDao, walletService, categoryService, transactionService, clockOn("2026-01-15"));
    }

    @Test
    void create_setsNextRunDate_toStartDateItself_whenDayOfMonthMatchesStartDate() {
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.valueOf(5000000), "Tiền nhà", 1, LocalDate.of(2026, 2, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        verify(walletService).requireOwnedByFamily(5L, 1L);
        verify(categoryService).requireOwnedByFamily(7L, 1L);
    }

    @Test
    void create_setsNextRunDate_toNextMonth_whenDayOfMonthAlreadyPassedInStartMonth() {
        // startDate is the 15th but the bill runs on the 1st — the 1st of the start
        // month already passed, so the first real occurrence is next month's 1st.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, 1, LocalDate.of(2026, 2, 15), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void create_floorsNextRunDateToToday_whenStartDateIsMonthsInThePast() {
        // Clock is fixed at 2026-01-15 (see setUp). A rule created today with a start
        // date months ago must never backfill — its first run should be the next real
        // occurrence from today, not a catch-up burst the moment the scheduler next runs.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.valueOf(5000000), "Tiền nhà", 1, LocalDate.of(2025, 10, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void create_startsThisMonth_whenPastStartDateButDayOfMonthHasNotPassedYet() {
        // Same fixed "today" (Jan 15) but dayOfMonth (20) hasn't happened yet this month
        // — the rule should still be allowed to fire later this month, not skip to next.
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, 20, LocalDate.of(2025, 6, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void update_floorsNextRunDateToToday_whenStartDateIsInThePast() {
        RecurringTransaction r = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(r));
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, 1, LocalDate.of(2025, 10, 1), null);

        var response = service.update(9L, 1L, request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void create_clampsDayOfMonth_toLastDayOfShorterMonth() {
        var request = new CreateRecurringTransactionRequest(
                5L, 7L, "EXPENSE", BigDecimal.TEN, null, 31, LocalDate.of(2026, 2, 1), null);

        var response = service.create(1L, 10L, "a@b.com", "An", request);

        assertThat(response.nextRunDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void generateDueTransactions_createsOneTransaction_andAdvancesToNextMonth() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        assertThat(rule.getLastRunDate()).isNull(); // never fired yet — frontend shows "Chưa thực hiện"
        when(recurringTransactionDao.selectDue(LocalDate.of(2026, 1, 15))).thenReturn(List.of(rule));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, "EXPENSE", BigDecimal.TEN,
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
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, "EXPENSE", BigDecimal.TEN, null, null, false, null));

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
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 10L, "EXPENSE", BigDecimal.TEN, null, null, false, null));

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
                .thenReturn(new TransactionResponse(99L, 5L, 7L, 2L, 10L, "EXPENSE", BigDecimal.TEN, null, null, false, null));

        service.generateDueTransactions();

        verify(recurringTransactionDao, never()).update(failing);
        verify(recurringTransactionDao).update(ok);
    }

    @Test
    void setActive_updatesFlag_onOwnedRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.setActive(9L, 1L, false);

        assertThat(rule.getActive()).isFalse();
        verify(recurringTransactionDao).update(rule);
    }

    @Test
    void delete_removesOwnedRule() {
        RecurringTransaction rule = rule(1L, LocalDate.of(2026, 1, 1), 1, null);
        when(recurringTransactionDao.selectById(9L)).thenReturn(Optional.of(rule));

        service.delete(9L, 1L);

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
        r.setDayOfMonth(dayOfMonth);
        r.setStartDate(nextRunDate);
        r.setEndDate(endDate);
        r.setNextRunDate(nextRunDate);
        r.setActive(true);
        r.setCreatedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime());
        return r;
    }
}
