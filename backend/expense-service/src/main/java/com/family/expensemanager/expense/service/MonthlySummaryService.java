package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.expense.dao.MonthlySummaryRunDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.MonthlySummaryRun;
import com.family.expensemanager.expense.dto.BudgetStatusResponse;
import com.family.expensemanager.expense.dto.CompareReportResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README C7 "Email tổng kết tháng": on the 1st, every family gets last month's income, expense, top categories,
 * the change against the month before and how its budgets ended — in app and by email (each member may switch the
 * email off in the notification settings). MONTHLY_SUMMARY_RUNS keeps it to one per family and month.
 *
 * @author boyquynhluu
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "MonthlySummaryService")
public class MonthlySummaryService {

    private static final int TOP_CATEGORIES = 3;

    private final WalletDao walletDao;
    private final MonthlySummaryRunDao runDao;
    private final ReportService reportService;
    private final BudgetMonitor budgetMonitor;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public void sendPreviousMonth() {
        YearMonth month = YearMonth.now(clock).minusMonths(1);
        for (Long familyId : walletDao.selectActiveFamilyIds()) {
            try {
                if (runDao.selectByFamilyAndMonth(familyId, month.toString()).isEmpty()) {
                    send(familyId, month);
                }
            } catch (Exception e) {
                log.warn("Không gửi được tổng kết tháng {} cho familyId={}", month, familyId, e);
            }
        }
    }

    /** The OWNER sends a month's summary now (e.g. to try it) — once per month, like the scheduled run. */
    @PreAuthorize("hasRole('OWNER')")
    public void sendNow(Long familyId, String yearMonth) {
        YearMonth month;
        try {
            month = YearMonth.parse(yearMonth);
        } catch (DateTimeParseException | NullPointerException e) {
            throw logged(log, new BadRequestException("Tháng không hợp lệ (định dạng yyyy-MM)"));
        }
        if (!month.isBefore(YearMonth.now(clock))) {
            throw logged(log, new BadRequestException("Chỉ gửi được tổng kết của tháng đã qua"));
        }
        if (runDao.selectByFamilyAndMonth(familyId, month.toString()).isPresent()) {
            throw logged(log, new ConflictException("Tổng kết tháng " + month + " đã được gửi"));
        }
        send(familyId, month);
    }

    private void send(Long familyId, YearMonth month) {
        CompareReportResponse report = reportService.compare(familyId, month.toString(), month.minusMonths(1).toString());
        BigDecimal income = report.current().income();
        BigDecimal expense = report.current().expense();
        StringBuilder message = new StringBuilder()
                .append("Thu: ").append(CurrencyUtil.formatCurrency(income))
                .append(" (").append(signed(report.incomeDelta())).append(" so với tháng trước)\n")
                .append("Chi: ").append(CurrencyUtil.formatCurrency(expense))
                .append(" (").append(signed(report.expenseDelta())).append(" so với tháng trước)\n")
                .append("Chênh lệch: ").append(signed(income.subtract(expense)));

        List<CompareReportResponse.CategoryCompareItem> top = report.categories().stream()
                .filter(c -> c.current().signum() > 0)
                .sorted(Comparator.comparing(CompareReportResponse.CategoryCompareItem::current).reversed())
                .limit(TOP_CATEGORIES)
                .toList();
        if (!top.isEmpty()) {
            message.append("\nChi nhiều nhất:");
            for (CompareReportResponse.CategoryCompareItem item : top) {
                message.append("\n  • ").append(categoryName(familyId, item.categoryId())).append(": ")
                        .append(CurrencyUtil.formatCurrency(item.current()));
            }
        }
        List<BudgetStatusResponse> budgets = budgetMonitor.status(familyId, month).stream()
                .filter(b -> BudgetMonitor.PERIOD_MONTH.equals(b.periodType()))
                .toList();
        if (!budgets.isEmpty()) {
            long over = budgets.stream().filter(b -> b.spent().compareTo(b.effectiveLimit()) > 0).count();
            message.append("\nNgân sách: ").append(budgets.size() - over).append("/").append(budgets.size())
                    .append(" trong hạn mức").append(over > 0 ? ", " + over + " vượt hạn mức" : "");
        }

        MonthlySummaryRun run = new MonthlySummaryRun();
        run.setFamilyId(familyId);
        run.setPeriodMonth(month.toString());
        run.setSentAt(LocalDateTime.now(clock));
        runDao.insert(run);
        eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.MONTHLY_SUMMARY, familyId, null, null, null, null,
                "Tổng kết chi tiêu tháng " + month.getMonthValue() + "/" + month.getYear(), message.toString(),
                "/reports"));
    }

    private String categoryName(Long familyId, Long categoryId) {
        try {
            return categoryService.requireOwnedByFamily(categoryId, familyId).getName();
        } catch (RuntimeException e) {
            return "Danh mục #" + categoryId;
        }
    }

    private static String signed(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + CurrencyUtil.formatCurrency(value);
    }
}
