package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.TransactionDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private TransactionDao transactionDao;
    @Mock
    private CategoryService categoryService;

    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(transactionDao, categoryService);
    }

    @Test
    void summary_computesBalance_asIncomeMinusExpense() {
        when(transactionDao.sumAmountByFamilyPeriodAndType(1L, "2026-01", "INCOME"))
                .thenReturn(BigDecimal.valueOf(500));
        when(transactionDao.sumAmountByFamilyPeriodAndType(1L, "2026-01", "EXPENSE"))
                .thenReturn(BigDecimal.valueOf(200));

        var response = summaryService.summary(1L, "2026-01");

        assertThat(response.totalIncome()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.totalExpense()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void trend_returnsOneEntryPerMonth_oldestFirst() {
        when(transactionDao.sumAmountByFamilyPeriodAndType(eq(1L), anyString(), any()))
                .thenReturn(BigDecimal.ZERO);

        var trend = summaryService.trend(1L, 3);

        assertThat(trend).hasSize(3);
        YearMonth current = YearMonth.now();
        assertThat(trend.get(0).yearMonth()).isEqualTo(current.minusMonths(2).toString());
        assertThat(trend.get(2).yearMonth()).isEqualTo(current.toString());
    }

    @Test
    void trend_throwsBadRequest_whenMonthsOutOfRange() {
        assertThatThrownBy(() -> summaryService.trend(1L, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> summaryService.trend(1L, 25)).isInstanceOf(BadRequestException.class);
    }
}
