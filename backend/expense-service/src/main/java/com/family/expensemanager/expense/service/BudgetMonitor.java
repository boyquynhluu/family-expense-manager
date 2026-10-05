package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.dto.BudgetStatusResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Budgets at work (README mục 2, 18 and A6): how much a budget has spent, its effective limit with rollover, and
 * the 80% / 100% crossing notifications when an expense is recorded. A budget may be monthly or yearly and scoped
 * to a category (with its sub-categories, C6), a wallet and/or a member; spending is read per category line, so a
 * split transaction (C5) counts each part against its own category.
 *
 * @author boyquynhluu
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "BudgetMonitor")
public class BudgetMonitor {

    static final String PERIOD_MONTH = "MONTH";
    static final String PERIOD_YEAR = "YEAR";
    private static final BigDecimal WARNING_RATIO = new BigDecimal("0.8");
    private static final String OVERALL_LABEL = "Tổng chi tiêu";

    /** One category part of a recorded expense (a split transaction has several). */
    public record Line(Long categoryId, BigDecimal amount) {
    }

    private final BudgetDao budgetDao;
    private final TransactionDao transactionDao;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Called right after an expense is inserted (same DB transaction, so the sums include it): for every budget
     * the expense falls into, publishes BUDGET_WARNING when it crosses 80% (still within the limit) or
     * BUDGET_EXCEEDED when it crosses 100% — each threshold once per crossing, as before README A6.
     */
    public void onExpenseRecorded(Long familyId, Long userId, String userEmail, String userName, Transaction tx,
                                  List<Line> lines) {
        LocalDate day = tx.getOccurredAt().toLocalDate();
        for (Budget budget : budgetDao.selectApplicable(familyId, YearMonth.from(day).toString(),
                String.valueOf(day.getYear()))) {
            if (budget.getWalletId() != null && !budget.getWalletId().equals(tx.getWalletId())) {
                continue;
            }
            if (budget.getUserId() != null && !budget.getUserId().equals(tx.getUserId())) {
                continue;
            }
            List<Long> categories = categoryScope(budget, familyId);
            BigDecimal contribution = lines.stream()
                    .filter(l -> categories == null || categories.contains(l.categoryId()))
                    .map(Line::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (contribution.signum() <= 0) {
                continue;
            }
            BigDecimal after = spent(budget, familyId, categories);
            BigDecimal before = after.subtract(contribution);
            BigDecimal limit = effectiveLimit(budget, familyId);
            String eventType = null;
            if (before.compareTo(limit) <= 0 && after.compareTo(limit) > 0) {
                eventType = ExpenseEvent.BUDGET_EXCEEDED;
            } else if (before.compareTo(limit.multiply(WARNING_RATIO)) < 0
                    && after.compareTo(limit.multiply(WARNING_RATIO)) >= 0 && after.compareTo(limit) <= 0) {
                eventType = ExpenseEvent.BUDGET_WARNING;
            }
            if (eventType == null) {
                continue;
            }
            boolean hidden = Boolean.TRUE.equals(tx.getIsPrivate());
            eventPublisher.publishEvent(new ExpenseEvent(eventType, familyId, userId, hidden ? null : tx.getId(),
                    budget.getCategoryId(), hidden ? null : tx.getAmount(), budget.getPeriodMonth(), limit, after,
                    label(budget, familyId), userEmail, userName, Instant.now()));
        }
    }

    /** README A6: every budget covering {@code yearMonth} (its monthly ones and its year's yearly ones). */
    public List<BudgetStatusResponse> status(Long familyId, YearMonth yearMonth) {
        List<BudgetStatusResponse> result = new ArrayList<>();
        for (Budget budget : budgetDao.selectApplicable(familyId, yearMonth.toString(),
                String.valueOf(yearMonth.getYear()))) {
            List<Long> categories = categoryScope(budget, familyId);
            BigDecimal spent = spent(budget, familyId, categories);
            BigDecimal carried = carriedOver(budget, familyId);
            BigDecimal effective = budget.getLimitAmount().add(carried);
            int percent = effective.signum() > 0
                    ? spent.multiply(BigDecimal.valueOf(100)).divide(effective, 0, RoundingMode.DOWN).intValue() : 0;
            result.add(new BudgetStatusResponse(budget.getId(), budget.getCategoryId(), budget.getWalletId(),
                    budget.getUserId(), budget.getPeriodType(), budget.getPeriodMonth(), budget.getLimitAmount(),
                    carried, effective, spent, percent));
        }
        return result;
    }

    BigDecimal effectiveLimit(Budget budget, Long familyId) {
        return budget.getLimitAmount().add(carriedOver(budget, familyId));
    }

    /**
     * README A6 rollover: what the same-scope budget of the previous period left unspent (never negative — an
     * overspent month doesn't shrink the next one). One period back only, so a chain of rollovers stays cheap.
     */
    BigDecimal carriedOver(Budget budget, Long familyId) {
        if (!Boolean.TRUE.equals(budget.getRollover())) {
            return BigDecimal.ZERO;
        }
        String previous = PERIOD_YEAR.equals(budget.getPeriodType())
                ? String.valueOf(Integer.parseInt(budget.getPeriodMonth().trim()) - 1)
                : YearMonth.parse(budget.getPeriodMonth()).minusMonths(1).toString();
        return budgetDao.selectSameScope(familyId, budget.getCategoryId(), budget.getWalletId(), budget.getUserId(),
                        budget.getPeriodType(), previous)
                .map(prev -> prev.getLimitAmount().subtract(spent(prev, familyId, categoryScope(prev, familyId)))
                        .max(BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal spent(Budget budget, Long familyId, List<Long> categories) {
        LocalDate from;
        LocalDate to;
        if (PERIOD_YEAR.equals(budget.getPeriodType())) {
            Year year = Year.parse(budget.getPeriodMonth().trim());
            from = year.atDay(1);
            to = year.plusYears(1).atDay(1);
        } else {
            YearMonth month = YearMonth.parse(budget.getPeriodMonth());
            from = month.atDay(1);
            to = month.plusMonths(1).atDay(1);
        }
        return transactionDao.sumExpenseForBudget(familyId, categories == null, categories == null ? List.of() : categories,
                budget.getWalletId(), budget.getUserId(), from, to);
    }

    /** Null = every category; otherwise the budget's category and its sub-categories (README C6). */
    private List<Long> categoryScope(Budget budget, Long familyId) {
        if (budget.getCategoryId() == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        ids.add(budget.getCategoryId());
        ids.addAll(categoryService.childIdsOf(budget.getCategoryId(), familyId));
        return ids;
    }

    private String label(Budget budget, Long familyId) {
        StringBuilder label = new StringBuilder();
        if (budget.getCategoryId() == null) {
            label.append(OVERALL_LABEL);
        } else {
            try {
                label.append(categoryService.requireOwnedByFamily(budget.getCategoryId(), familyId).getName());
            } catch (RuntimeException e) {
                label.append("Danh mục #").append(budget.getCategoryId());
            }
        }
        if (budget.getWalletId() != null) {
            label.append(" · ví #").append(budget.getWalletId());
        }
        if (Objects.equals(budget.getPeriodType(), PERIOD_YEAR)) {
            label.append(" (cả năm)");
        }
        return label.toString();
    }
}
