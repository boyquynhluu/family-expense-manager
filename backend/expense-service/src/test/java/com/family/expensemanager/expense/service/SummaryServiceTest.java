package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.WalletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

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
    @Mock
    private WalletService walletService;

    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(transactionDao, categoryService, walletService);
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

    @Test
    void walletCategoryBreakdown_omitsZeroTotals_andOnlyConsidersExpenseCategories() {
        WalletResponse wallet = new WalletResponse(10L, 1L, "Ví chính", "VND", BigDecimal.ZERO, BigDecimal.ZERO);
        CategoryResponse expenseCategory = new CategoryResponse(20L, 1L, "Ăn uống", "EXPENSE", null, null);
        CategoryResponse incomeCategory = new CategoryResponse(21L, 1L, "Lương", "INCOME", null, null);
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(expenseCategory, incomeCategory));
        when(transactionDao.sumAmountByWalletCategoryPeriodAndType(1L, 10L, 20L, "2026-01", "EXPENSE"))
                .thenReturn(BigDecimal.valueOf(150));

        var breakdown = summaryService.walletCategoryBreakdown(1L, "2026-01");

        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).walletId()).isEqualTo(10L);
        assertThat(breakdown.get(0).categoryId()).isEqualTo(20L);
        assertThat(breakdown.get(0).total()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }
}
