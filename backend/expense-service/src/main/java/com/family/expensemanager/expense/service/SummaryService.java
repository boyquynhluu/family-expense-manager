package com.family.expensemanager.expense.service;

import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dto.CategoryReportItem;
import com.family.expensemanager.expense.dto.SummaryResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SummaryService {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";

    private final TransactionDao transactionDao;
    private final CategoryService categoryService;

    public SummaryService(TransactionDao transactionDao, CategoryService categoryService) {
        this.transactionDao = transactionDao;
        this.categoryService = categoryService;
    }

    @Cacheable(cacheNames = "expense:summary", key = "#familyId + ':' + #yearMonth")
    public SummaryResponse summary(Long familyId, String yearMonth) {
        BigDecimal totalIncome = transactionDao.sumAmountByFamilyPeriodAndType(familyId, yearMonth, TYPE_INCOME);
        BigDecimal totalExpense = transactionDao.sumAmountByFamilyPeriodAndType(familyId, yearMonth, TYPE_EXPENSE);
        return new SummaryResponse(yearMonth, totalIncome, totalExpense, totalIncome.subtract(totalExpense));
    }

    @Cacheable(cacheNames = "expense:report:category", key = "#familyId + ':' + #yearMonth")
    public List<CategoryReportItem> reportByCategory(Long familyId, String yearMonth) {
        return categoryService.listByFamily(familyId).stream()
                .filter(c -> TYPE_EXPENSE.equals(c.type()))
                .map(c -> new CategoryReportItem(c.id(), c.name(),
                        transactionDao.sumAmountByCategoryPeriodAndType(familyId, c.id(), yearMonth, TYPE_EXPENSE)))
                .toList();
    }
}
