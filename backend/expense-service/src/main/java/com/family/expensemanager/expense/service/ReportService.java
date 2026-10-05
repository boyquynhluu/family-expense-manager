package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.ReportDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CompareReportResponse;
import com.family.expensemanager.expense.dto.CompareReportResponse.CategoryCompareItem;
import com.family.expensemanager.expense.dto.CompareReportResponse.MonthSummary;
import com.family.expensemanager.expense.dto.MemberReportItem;
import com.family.expensemanager.expense.dto.RangeReportResponse;
import com.family.expensemanager.expense.dto.RangeReportResponse.BucketTotal;
import com.family.expensemanager.expense.dto.RangeReportResponse.CategoryTotal;
import com.family.expensemanager.expense.dto.WalletMonthlyItem;
import com.family.expensemanager.expense.dto.YearReportResponse;
import com.family.expensemanager.expense.dto.YearReportResponse.MonthTotal;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * @author boyquynhluu
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j(topic = "ReportService")
public class ReportService {

    static final int MAX_RANGE_DAYS = 366;
    static final int MAX_DAILY_BUCKET_DAYS = 62;
    static final String BUCKET_DAY = "DAY";
    static final String BUCKET_MONTH = "MONTH";
    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";

    private final ReportDao reportDao;
    private final WalletDao walletDao;

    public RangeReportResponse range(Long familyId, String from, String to) {
        try {
            log.info("range - start, familyId={}, from={}, to={}", familyId, from, to);
            LocalDate fromDate = parseDate(from, "Ngày bắt đầu");
            LocalDate toDate = parseDate(to, "Ngày kết thúc");
            long days = validateRange(fromDate, toDate);
            LocalDate toExclusive = toDate.plusDays(1);

            Totals grand = new Totals();
            List<CategoryTotal> byCategory = new ArrayList<>();
            for (Map<String, Object> row : reportDao.sumByCategoryAndType(familyId, fromDate, toExclusive)) {
                String type = str(row.get("type"));
                BigDecimal total = decimal(row.get("total"));
                grand.add(type, total);
                byCategory.add(new CategoryTotal(longValue(row.get("categoryId")), type, total));
            }
            byCategory.sort(Comparator.comparing(CategoryTotal::total).reversed());

            boolean daily = days <= MAX_DAILY_BUCKET_DAYS;
            Map<String, Totals> buckets = new LinkedHashMap<>();
            List<Map<String, Object>> bucketRows;
            if (daily) {
                for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
                    buckets.put(d.toString(), new Totals());
                }
                bucketRows = reportDao.sumByDayAndType(familyId, fromDate, toExclusive);
            } else {
                YearMonth last = YearMonth.from(toDate);
                for (YearMonth m = YearMonth.from(fromDate); !m.isAfter(last); m = m.plusMonths(1)) {
                    buckets.put(m.toString(), new Totals());
                }
                bucketRows = reportDao.sumByMonthAndType(familyId, fromDate, toExclusive);
            }
            for (Map<String, Object> row : bucketRows) {
                buckets.computeIfAbsent(str(row.get("bucket")), k -> new Totals())
                        .add(str(row.get("type")), decimal(row.get("total")));
            }
            List<BucketTotal> bucketList = buckets.entrySet().stream()
                    .map(e -> new BucketTotal(e.getKey(), e.getValue().income, e.getValue().expense))
                    .toList();

            return new RangeReportResponse(fromDate.toString(), toDate.toString(), daily ? BUCKET_DAY : BUCKET_MONTH,
                    grand.income, grand.expense, grand.income.subtract(grand.expense), byCategory, bucketList);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.range", e);
        }
    }

    public YearReportResponse year(Long familyId, String year) {
        try {
            log.info("year - start, familyId={}, year={}", familyId, year);
            int y = parseYear(year);
            Map<String, Totals> months = new LinkedHashMap<>();
            for (int m = 1; m <= 12; m++) {
                months.put(YearMonth.of(y, m).toString(), new Totals());
            }
            Totals grand = new Totals();
            List<Map<String, Object>> rows =
                    reportDao.sumByMonthAndType(familyId, LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1));
            for (Map<String, Object> row : rows) {
                Totals bucket = months.get(str(row.get("bucket")));
                if (bucket == null) {
                    continue;
                }
                String type = str(row.get("type"));
                BigDecimal total = decimal(row.get("total"));
                bucket.add(type, total);
                grand.add(type, total);
            }
            List<MonthTotal> list = months.entrySet().stream()
                    .map(e -> new MonthTotal(e.getKey(), e.getValue().income, e.getValue().expense))
                    .toList();
            return new YearReportResponse(y, grand.income, grand.expense, grand.income.subtract(grand.expense), list);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.year", e);
        }
    }

    public List<MemberReportItem> byMember(Long familyId, String from, String to) {
        try {
            log.info("byMember - start, familyId={}, from={}, to={}", familyId, from, to);
            LocalDate fromDate = parseDate(from, "Ngày bắt đầu");
            LocalDate toDate = parseDate(to, "Ngày kết thúc");
            validateRange(fromDate, toDate);
            LocalDate toExclusive = toDate.plusDays(1);

            Map<Long, Totals> byUser = new LinkedHashMap<>();
            for (Map<String, Object> row : reportDao.sumByUserAndType(familyId, fromDate, toExclusive)) {
                byUser.computeIfAbsent(longValue(row.get("userId")), k -> new Totals())
                        .add(str(row.get("type")), decimal(row.get("total")));
            }
            Map<Long, String> names = new HashMap<>();
            for (Map<String, Object> row : reportDao.selectLatestMemberNames(familyId, fromDate, toExclusive)) {
                names.put(longValue(row.get("userId")), str(row.get("displayName")));
            }
            return byUser.entrySet().stream()
                    .map(e -> new MemberReportItem(
                            e.getKey(), names.get(e.getKey()), e.getValue().income, e.getValue().expense))
                    .sorted(Comparator.comparing(MemberReportItem::expense).reversed())
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.byMember", e);
        }
    }

    public CompareReportResponse compare(Long familyId, String month, String withMonth) {
        try {
            log.info("compare - start, familyId={}, month={}, withMonth={}", familyId, month, withMonth);
            YearMonth current = parseMonth(month, "Tháng");
            YearMonth previous = parseMonth(withMonth, "Tháng so sánh");

            Totals currentTotals = new Totals();
            Totals previousTotals = new Totals();
            Map<Long, BigDecimal> currentByCategory = expenseByCategory(familyId, current, currentTotals);
            Map<Long, BigDecimal> previousByCategory = expenseByCategory(familyId, previous, previousTotals);

            Set<Long> categoryIds = new LinkedHashSet<>(currentByCategory.keySet());
            categoryIds.addAll(previousByCategory.keySet());
            List<CategoryCompareItem> categories = new ArrayList<>();
            for (Long categoryId : categoryIds) {
                BigDecimal cur = currentByCategory.getOrDefault(categoryId, BigDecimal.ZERO);
                BigDecimal prev = previousByCategory.getOrDefault(categoryId, BigDecimal.ZERO);
                categories.add(new CategoryCompareItem(categoryId, cur, prev, cur.subtract(prev)));
            }
            categories.sort(Comparator.comparing(CategoryCompareItem::current).reversed()
                    .thenComparing(Comparator.comparing(CategoryCompareItem::previous).reversed()));

            return new CompareReportResponse(
                    new MonthSummary(current.toString(), currentTotals.income, currentTotals.expense),
                    new MonthSummary(previous.toString(), previousTotals.income, previousTotals.expense),
                    currentTotals.income.subtract(previousTotals.income),
                    currentTotals.expense.subtract(previousTotals.expense),
                    categories);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.compare", e);
        }
    }

    /** README C6: income/expense per tag between two dates (inclusive), tags without activity left out. */
    public List<com.family.expensemanager.expense.dto.TagReportItem> byTag(Long familyId, String from, String to) {
        try {
            log.info("byTag - start, familyId={}, from={}, to={}", familyId, from, to);
            LocalDate fromDate = parseDate(from, "Ngày bắt đầu");
            LocalDate toDate = parseDate(to, "Ngày kết thúc");
            validateRange(fromDate, toDate);
            Map<Long, com.family.expensemanager.expense.dto.TagReportItem> items = new java.util.LinkedHashMap<>();
            for (Map<String, Object> row : reportDao.sumByTagAndType(familyId, fromDate, toDate.plusDays(1))) {
                Long tagId = longValue(row.get("tagId"));
                var item = items.getOrDefault(tagId, new com.family.expensemanager.expense.dto.TagReportItem(
                        tagId, str(row.get("name")), BigDecimal.ZERO, BigDecimal.ZERO));
                BigDecimal total = decimal(row.get("total"));
                items.put(tagId, TYPE_EXPENSE.equals(str(row.get("type")))
                        ? new com.family.expensemanager.expense.dto.TagReportItem(tagId, item.name(), item.income(), item.expense().add(total))
                        : new com.family.expensemanager.expense.dto.TagReportItem(tagId, item.name(), item.income().add(total), item.expense()));
            }
            return new ArrayList<>(items.values());
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.byTag", e);
        }
    }

    /**
     * Per-wallet opening balance, income/expense, transfers in/out, balance adjustments, net (surplus or deficit) and
     * closing balance for one month. Opening balance = initial balance + every movement before the
     * month, i.e. the same formula as {@code WalletService.currentBalanceOf} cut off at the month start.
     */
    public List<WalletMonthlyItem> walletMonthly(Long familyId, String yearMonth) {
        try {
            log.info("walletMonthly - start, familyId={}, yearMonth={}", familyId, yearMonth);
            YearMonth month = parseMonth(yearMonth, "Tháng");
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.plusMonths(1).atDay(1);

            Map<Long, WalletFlows> before = walletFlows(familyId, null, monthStart);
            Map<Long, WalletFlows> during = walletFlows(familyId, monthStart, monthEnd);

            List<WalletMonthlyItem> result = new ArrayList<>();
            for (Wallet wallet : walletDao.selectByFamilyId(familyId)) {
                WalletFlows prior = before.getOrDefault(wallet.getId(), new WalletFlows());
                WalletFlows current = during.getOrDefault(wallet.getId(), new WalletFlows());
                BigDecimal opening = wallet.getInitialBalance().add(prior.net());
                BigDecimal net = current.net();
                result.add(new WalletMonthlyItem(
                        wallet.getId(), wallet.getName(), wallet.getCurrency(), opening,
                        current.totals.income, current.totals.expense, current.transferIn, current.transferOut,
                        current.adjustment, current.loanFlow, net, opening.add(net)));
            }
            return result;
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("ReportService.walletMonthly", e);
        }
    }

    private Map<Long, WalletFlows> walletFlows(Long familyId, LocalDate fromDate, LocalDate toExclusive) {
        Map<Long, WalletFlows> flows = new HashMap<>();
        for (Map<String, Object> row : reportDao.sumByWalletAndType(familyId, fromDate, toExclusive)) {
            flows.computeIfAbsent(longValue(row.get("walletId")), k -> new WalletFlows())
                    .totals.add(str(row.get("type")), decimal(row.get("total")));
        }
        for (Map<String, Object> row : reportDao.sumTransfersByWalletAndDirection(familyId, fromDate, toExclusive)) {
            WalletFlows wf = flows.computeIfAbsent(longValue(row.get("walletId")), k -> new WalletFlows());
            BigDecimal total = decimal(row.get("total"));
            String direction = str(row.get("direction"));
            if (DIRECTION_IN.equals(direction)) {
                wf.transferIn = wf.transferIn.add(total);
            } else if (DIRECTION_OUT.equals(direction)) {
                wf.transferOut = wf.transferOut.add(total);
            }
        }
        for (Map<String, Object> row : reportDao.sumAdjustmentsByWallet(familyId, fromDate, toExclusive)) {
            WalletFlows wf = flows.computeIfAbsent(longValue(row.get("walletId")), k -> new WalletFlows());
            wf.adjustment = wf.adjustment.add(decimal(row.get("total")));
        }
        for (Map<String, Object> row : reportDao.sumLoanFlowsByWallet(familyId, fromDate, toExclusive)) {
            WalletFlows wf = flows.computeIfAbsent(longValue(row.get("walletId")), k -> new WalletFlows());
            wf.loanFlow = wf.loanFlow.add(decimal(row.get("total")));
        }
        return flows;
    }

    private Map<Long, BigDecimal> expenseByCategory(Long familyId, YearMonth month, Totals totals) {
        Map<Long, BigDecimal> expenseByCategory = new HashMap<>();
        List<Map<String, Object>> rows =
                reportDao.sumByCategoryAndType(familyId, month.atDay(1), month.plusMonths(1).atDay(1));
        for (Map<String, Object> row : rows) {
            String type = str(row.get("type"));
            BigDecimal total = decimal(row.get("total"));
            totals.add(type, total);
            if (TYPE_EXPENSE.equals(type)) {
                expenseByCategory.merge(longValue(row.get("categoryId")), total, BigDecimal::add);
            }
        }
        return expenseByCategory;
    }

    private static long validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw logged(log, new BadRequestException("Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc"));
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw logged(log, new BadRequestException("Khoảng thời gian tối đa là " + MAX_RANGE_DAYS + " ngày"));
        }
        return days;
    }

    private static LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw logged(log, new BadRequestException(label + " không hợp lệ (định dạng yyyy-MM-dd)"));
        }
    }

    private static YearMonth parseMonth(String value, String label) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw logged(log, new BadRequestException(label + " không hợp lệ (định dạng yyyy-MM)"));
        }
    }

    private static int parseYear(String value) {
        int year;
        try {
            year = Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw logged(log, new BadRequestException("Năm không hợp lệ"));
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw logged(log, new BadRequestException("Năm phải trong khoảng " + MIN_YEAR + "-" + MAX_YEAR));
        }
        return year;
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : value.toString();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    // BIGINT UNSIGNED comes back as BigInteger from the MySQL driver, hence Number rather than Long.
    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static final class WalletFlows {
        private final Totals totals = new Totals();
        private BigDecimal transferIn = BigDecimal.ZERO;
        private BigDecimal transferOut = BigDecimal.ZERO;
        private BigDecimal adjustment = BigDecimal.ZERO;
        private BigDecimal loanFlow = BigDecimal.ZERO;

        BigDecimal net() {
            return totals.income.subtract(totals.expense).add(transferIn).subtract(transferOut).add(adjustment)
                    .add(loanFlow);
        }
    }

    private static final class Totals {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;

        void add(String type, BigDecimal amount) {
            if (TYPE_INCOME.equals(type)) {
                income = income.add(amount);
            } else if (TYPE_EXPENSE.equals(type)) {
                expense = expense.add(amount);
            }
        }
    }
}
