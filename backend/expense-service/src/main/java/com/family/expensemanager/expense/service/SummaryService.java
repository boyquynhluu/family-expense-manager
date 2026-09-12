package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dto.CategoryReportItem;
import com.family.expensemanager.expense.dto.SummaryResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "SummaryService")
public class SummaryService {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MAX_TREND_MONTHS = 24;

    private final TransactionDao transactionDao;
    private final CategoryService categoryService;

    @Cacheable(cacheNames = "expense:summary", key = "#familyId + ':' + #yearMonth")
    public SummaryResponse summary(Long familyId, String yearMonth) {
        log.info("summary - start, familyId={}, yearMonth={}", familyId, yearMonth);
        BigDecimal totalIncome = transactionDao.sumAmountByFamilyPeriodAndType(familyId, yearMonth, TYPE_INCOME);
        BigDecimal totalExpense = transactionDao.sumAmountByFamilyPeriodAndType(familyId, yearMonth, TYPE_EXPENSE);
        return new SummaryResponse(yearMonth, totalIncome, totalExpense, totalIncome.subtract(totalExpense));
    }

    @Cacheable(cacheNames = "expense:report:category", key = "#familyId + ':' + #yearMonth")
    public List<CategoryReportItem> reportByCategory(Long familyId, String yearMonth) {
        log.info("reportByCategory - start, familyId={}, yearMonth={}", familyId, yearMonth);
        return categoryService.listByFamily(familyId).stream()
                .filter(c -> TYPE_EXPENSE.equals(c.type()))
                .map(c -> new CategoryReportItem(c.id(), c.name(),
                        transactionDao.sumAmountByCategoryPeriodAndType(familyId, c.id(), yearMonth, TYPE_EXPENSE)))
                .toList();
    }

    /**
     * Income/expense/net for each of the last {@code months} months (oldest first), for
     * the Dashboard's spending-trend chart. Delegates to {@link #summary}, but that call
     * is a plain self-invocation so it bypasses the {@code @Cacheable} proxy — acceptable
     * here since the trend endpoint is called far less often than the single-month summary.
     */
    public List<SummaryResponse> trend(Long familyId, int months) {
        log.info("trend - start, familyId={}, months={}", familyId, months);
        if (months < 1 || months > MAX_TREND_MONTHS) {
            throw new BadRequestException("months phải trong khoảng 1-" + MAX_TREND_MONTHS);
        }
        YearMonth current = YearMonth.now();
        List<SummaryResponse> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            result.add(summary(familyId, current.minusMonths(i).toString()));
        }
        return result;
    }
}
