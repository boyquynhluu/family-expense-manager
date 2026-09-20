package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.ReportDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long FAMILY = 1L;

    @Mock
    private ReportDao reportDao;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportDao);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void range_rejectsFromAfterTo() {
        assertThatThrownBy(() -> reportService.range(FAMILY, "2026-02-10", "2026-02-01"))
                .isInstanceOf(BadRequestException.class);
        verify(reportDao, never()).sumByCategoryAndType(any(), any(), any());
    }

    @Test
    void range_rejectsUnparseableDates() {
        assertThatThrownBy(() -> reportService.range(FAMILY, "abc", "2026-02-01"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reportService.range(FAMILY, "2026-02-01", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void range_rejectsMoreThan366Days() {
        assertThatThrownBy(() -> reportService.range(FAMILY, "2026-01-01", "2027-01-02"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void range_accepts366DaysInclusive_andBucketsByMonth() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2027, 1, 1);
        when(reportDao.sumByCategoryAndType(FAMILY, from, to.plusDays(1))).thenReturn(List.of());
        when(reportDao.sumByMonthAndType(FAMILY, from, to.plusDays(1))).thenReturn(List.of());

        var response = reportService.range(FAMILY, "2026-01-01", "2027-01-01");

        assertThat(response.bucketType()).isEqualTo("MONTH");
        assertThat(response.buckets()).hasSize(13);
        assertThat(response.buckets().get(0).bucket()).isEqualTo("2026-01");
        assertThat(response.buckets().get(12).bucket()).isEqualTo("2027-01");
    }

    @Test
    void range_uses62DaysAsDailyBoundary() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        // 62 days inclusive: Jan 1 .. Mar 3 (2026 is not a leap year)
        when(reportDao.sumByCategoryAndType(FAMILY, from, LocalDate.of(2026, 3, 4))).thenReturn(List.of());
        when(reportDao.sumByDayAndType(FAMILY, from, LocalDate.of(2026, 3, 4))).thenReturn(List.of());

        var daily = reportService.range(FAMILY, "2026-01-01", "2026-03-03");

        assertThat(daily.bucketType()).isEqualTo("DAY");
        assertThat(daily.buckets()).hasSize(62);

        when(reportDao.sumByCategoryAndType(FAMILY, from, LocalDate.of(2026, 3, 5))).thenReturn(List.of());
        when(reportDao.sumByMonthAndType(FAMILY, from, LocalDate.of(2026, 3, 5))).thenReturn(List.of());

        var monthly = reportService.range(FAMILY, "2026-01-01", "2026-03-04");

        assertThat(monthly.bucketType()).isEqualTo("MONTH");
        assertThat(monthly.buckets()).hasSize(3);
    }

    @Test
    void range_computesTotalsNetAndZeroFillsDays() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate toExclusive = LocalDate.of(2026, 3, 4);
        when(reportDao.sumByCategoryAndType(FAMILY, from, toExclusive)).thenReturn(List.of(
                row("categoryId", java.math.BigInteger.valueOf(5), "type", "EXPENSE", "total", new BigDecimal("100.00")),
                row("categoryId", 6L, "type", "EXPENSE", "total", new BigDecimal("300.00")),
                row("categoryId", 7L, "type", "INCOME", "total", new BigDecimal("1000.00"))));
        when(reportDao.sumByDayAndType(FAMILY, from, toExclusive)).thenReturn(List.of(
                row("bucket", "2026-03-01", "type", "INCOME", "total", new BigDecimal("1000.00")),
                row("bucket", "2026-03-03", "type", "EXPENSE", "total", new BigDecimal("400.00"))));

        var response = reportService.range(FAMILY, "2026-03-01", "2026-03-03");

        assertThat(response.totalIncome()).isEqualByComparingTo("1000");
        assertThat(response.totalExpense()).isEqualByComparingTo("400");
        assertThat(response.net()).isEqualByComparingTo("600");
        assertThat(response.byCategory().get(0).total()).isEqualByComparingTo("1000");
        assertThat(response.byCategory()).extracting(c -> c.categoryId()).containsExactly(7L, 6L, 5L);
        assertThat(response.buckets()).hasSize(3);
        assertThat(response.buckets().get(0).income()).isEqualByComparingTo("1000");
        assertThat(response.buckets().get(1).income()).isEqualByComparingTo("0");
        assertThat(response.buckets().get(1).expense()).isEqualByComparingTo("0");
        assertThat(response.buckets().get(2).expense()).isEqualByComparingTo("400");
    }

    @Test
    void year_rejectsOutOfRangeAndNonNumeric() {
        assertThatThrownBy(() -> reportService.year(FAMILY, "1999")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reportService.year(FAMILY, "abcd")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void year_zeroFillsTwelveMonthsAndSumsTotals() {
        when(reportDao.sumByMonthAndType(FAMILY, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).thenReturn(List.of(
                row("bucket", "2026-02", "type", "INCOME", "total", new BigDecimal("500")),
                row("bucket", "2026-02", "type", "EXPENSE", "total", new BigDecimal("200")),
                row("bucket", "2026-12", "type", "EXPENSE", "total", new BigDecimal("50"))));

        var response = reportService.year(FAMILY, "2026");

        assertThat(response.months()).hasSize(12);
        assertThat(response.months().get(0).yearMonth()).isEqualTo("2026-01");
        assertThat(response.months().get(0).income()).isEqualByComparingTo("0");
        assertThat(response.months().get(1).income()).isEqualByComparingTo("500");
        assertThat(response.months().get(1).expense()).isEqualByComparingTo("200");
        assertThat(response.months().get(11).expense()).isEqualByComparingTo("50");
        assertThat(response.totalIncome()).isEqualByComparingTo("500");
        assertThat(response.totalExpense()).isEqualByComparingTo("250");
        assertThat(response.net()).isEqualByComparingTo("250");
    }

    @Test
    void byMember_sortsByExpenseDescending_andKeepsNullNameWhenUnknown() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate toExclusive = LocalDate.of(2026, 4, 1);
        when(reportDao.sumByUserAndType(FAMILY, from, toExclusive)).thenReturn(List.of(
                row("userId", 1L, "type", "EXPENSE", "total", new BigDecimal("100")),
                row("userId", 1L, "type", "INCOME", "total", new BigDecimal("900")),
                row("userId", java.math.BigInteger.valueOf(2), "type", "EXPENSE", "total", new BigDecimal("700"))));
        when(reportDao.selectLatestMemberNames(FAMILY, from, toExclusive))
                .thenReturn(List.of(row("userId", 1L, "displayName", "Lan")));

        var members = reportService.byMember(FAMILY, "2026-03-01", "2026-03-31");

        assertThat(members).hasSize(2);
        assertThat(members.get(0).userId()).isEqualTo(2L);
        assertThat(members.get(0).displayName()).isNull();
        assertThat(members.get(0).expense()).isEqualByComparingTo("700");
        assertThat(members.get(0).income()).isEqualByComparingTo("0");
        assertThat(members.get(1).displayName()).isEqualTo("Lan");
        assertThat(members.get(1).income()).isEqualByComparingTo("900");
    }

    @Test
    void byMember_rejectsInvalidRange() {
        assertThatThrownBy(() -> reportService.byMember(FAMILY, "2026-03-31", "2026-03-01"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void compare_rejectsBadMonth() {
        assertThatThrownBy(() -> reportService.compare(FAMILY, "2026-13", "2026-02"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reportService.compare(FAMILY, "2026-03", "nope"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void compare_computesDeltasAndPerCategoryExpense() {
        when(reportDao.sumByCategoryAndType(FAMILY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1))).thenReturn(List.of(
                row("categoryId", 1L, "type", "EXPENSE", "total", new BigDecimal("300")),
                row("categoryId", 2L, "type", "EXPENSE", "total", new BigDecimal("100")),
                row("categoryId", 9L, "type", "INCOME", "total", new BigDecimal("1000"))));
        when(reportDao.sumByCategoryAndType(FAMILY, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1))).thenReturn(List.of(
                row("categoryId", 1L, "type", "EXPENSE", "total", new BigDecimal("200")),
                row("categoryId", 3L, "type", "EXPENSE", "total", new BigDecimal("50")),
                row("categoryId", 9L, "type", "INCOME", "total", new BigDecimal("1200"))));

        var response = reportService.compare(FAMILY, "2026-03", "2026-02");

        assertThat(response.current().yearMonth()).isEqualTo("2026-03");
        assertThat(response.previous().yearMonth()).isEqualTo("2026-02");
        assertThat(response.current().expense()).isEqualByComparingTo("400");
        assertThat(response.previous().expense()).isEqualByComparingTo("250");
        assertThat(response.expenseDelta()).isEqualByComparingTo("150");
        assertThat(response.incomeDelta()).isEqualByComparingTo("-200");
        assertThat(response.categories()).extracting(c -> c.categoryId()).containsExactly(1L, 2L, 3L);
        assertThat(response.categories().get(0).delta()).isEqualByComparingTo("100");
        assertThat(response.categories().get(1).previous()).isEqualByComparingTo("0");
        assertThat(response.categories().get(1).delta()).isEqualByComparingTo("100");
        assertThat(response.categories().get(2).current()).isEqualByComparingTo("0");
        assertThat(response.categories().get(2).delta()).isEqualByComparingTo("-50");
    }
}
