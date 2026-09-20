package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.dto.CreateRecurringTransactionRequest;
import com.family.expensemanager.expense.dto.RecurringTransactionResponse;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CRUD for recurring-bill templates (rent, internet, subscriptions — see README
 * "3. Không có giao dịch định kỳ") plus the daily catch-up job that turns a due
 * template into a real transaction. See {@link RecurringTransactionScheduler} for the
 * cron trigger.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "RecurringTransactionService")
public class RecurringTransactionService {

    /** Caps how many missed months a single run backfills for one rule, so a rule left
     *  inactive for years can't spawn years of back-dated transactions in one go. */
    private static final int MAX_CATCH_UP_RUNS = 24;

    private static final int MAX_PAGE_SIZE = 100;

    private static final String FREQUENCY_MONTHLY = "MONTHLY";
    private static final String FREQUENCY_WEEKLY = "WEEKLY";
    private static final String FREQUENCY_YEARLY = "YEARLY";

    /** day_of_month is NOT NULL in the schema, so WEEKLY rules store this unused value. */
    private static final int WEEKLY_PLACEHOLDER_DAY_OF_MONTH = 1;

    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RecurringTransactionResponse create(
            Long familyId, Long userId, String userEmail, String userDisplayName,
            CreateRecurringTransactionRequest request) {
        log.info("create - start, familyId={}, walletId={}, categoryId={}",
                familyId, request.walletId(), request.categoryId());
        walletService.requireOwnedByFamily(request.walletId(), familyId);
        categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        RecurringTransaction r = new RecurringTransaction();
        r.setFamilyId(familyId);
        r.setWalletId(request.walletId());
        r.setCategoryId(request.categoryId());
        r.setCreatedByUserId(userId);
        r.setCreatedByEmail(userEmail);
        r.setCreatedByDisplayName(userDisplayName);
        r.setType(request.type());
        r.setAmount(request.amount());
        r.setNote(request.note());
        applySchedule(r, request);
        r.setActive(true);
        r.setCreatedAt(LocalDateTime.now(clock));

        recurringTransactionDao.insert(r);
        return RecurringTransactionResponse.from(r);
    }

    public PageResponse<RecurringTransactionResponse> listByFamilyPaged(Long familyId, int page, int size) {
        log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = recurringTransactionDao.countByFamilyId(familyId);
        List<RecurringTransactionResponse> content =
                recurringTransactionDao.selectByFamilyIdPaged(familyId, size, page * size).stream()
                        .map(RecurringTransactionResponse::from)
                        .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    public RecurringTransactionResponse update(
            Long id, Long familyId, Long callerUserId, boolean callerIsOwner,
            CreateRecurringTransactionRequest request) {
        log.info("update - start, id={}, familyId={}", id, familyId);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
        requireCanModify(r, callerUserId, callerIsOwner);
        walletService.requireOwnedByFamily(request.walletId(), familyId);
        categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        r.setWalletId(request.walletId());
        r.setCategoryId(request.categoryId());
        r.setType(request.type());
        r.setAmount(request.amount());
        r.setNote(request.note());
        applySchedule(r, request);
        recurringTransactionDao.update(r);
        return RecurringTransactionResponse.from(r);
    }

    @Transactional
    public void setActive(Long id, Long familyId, Long callerUserId, boolean callerIsOwner, boolean active) {
        log.info("setActive - start, id={}, familyId={}, active={}", id, familyId, active);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
        requireCanModify(r, callerUserId, callerIsOwner);
        r.setActive(active);
        recurringTransactionDao.update(r);
    }

    @Transactional
    public void delete(Long id, Long familyId, Long callerUserId, boolean callerIsOwner) {
        log.info("delete - start, id={}, familyId={}", id, familyId);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
        requireCanModify(r, callerUserId, callerIsOwner);
        recurringTransactionDao.delete(r);
    }

    /**
     * Called daily by {@link RecurringTransactionScheduler}. For every family's due
     * rule, creates a real transaction via {@link TransactionService#create} (so budget-
     * crossing emails etc. fire the same as a manually entered transaction), then
     * advances the rule to its next occurrence. Failures on one rule are logged and
     * skipped rather than aborting the whole run, since rules across different families
     * are independent.
     */
    public void generateDueTransactions() {
        LocalDate today = LocalDate.now(clock);
        List<RecurringTransaction> due = recurringTransactionDao.selectDue(today);
        log.info("generateDueTransactions - start, today={}, dueCount={}", today, due.size());
        for (RecurringTransaction r : due) {
            try {
                processDueRule(r, today);
            } catch (Exception e) {
                log.error("Không xử lý được recurring transaction id={}, familyId={}", r.getId(), r.getFamilyId(), e);
                publishRecurringEvent(ExpenseEvent.RECURRING_FAILED, r, null, r.getNextRunDate());
            }
        }
    }

    /** Never throws — a notification problem must not abort the scheduler run or mask the rule's own outcome. */
    private void publishRecurringEvent(String eventType, RecurringTransaction r, Long transactionId,
                                       LocalDate occurredOn) {
        try {
            eventPublisher.publishEvent(new ExpenseEvent(
                    eventType, r.getFamilyId(), r.getCreatedByUserId(), transactionId, r.getCategoryId(),
                    r.getAmount(), null, null, null, categoryNameOf(r), r.getCreatedByEmail(),
                    r.getCreatedByDisplayName(), Instant.now(), occurredOn, r.getNote()));
        } catch (Exception e) {
            log.warn("Không publish được event {} cho recurring transaction id={}", eventType, r.getId(), e);
        }
    }

    private String categoryNameOf(RecurringTransaction r) {
        try {
            Category category = categoryService.requireOwnedByFamily(r.getCategoryId(), r.getFamilyId());
            return category == null ? null : category.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private void processDueRule(RecurringTransaction r, LocalDate today) {
        LocalDate runDate = r.getNextRunDate();
        int runs = 0;
        while (!runDate.isAfter(today) && runs < MAX_CATCH_UP_RUNS) {
            TransactionRequest request = new TransactionRequest(
                    r.getWalletId(), r.getCategoryId(), r.getType(), r.getAmount(), runDate.atStartOfDay(),
                    r.getNote());
            TransactionResponse created = transactionService.create(
                    r.getFamilyId(), r.getCreatedByUserId(), r.getCreatedByEmail(), r.getCreatedByDisplayName(),
                    request);
            publishRecurringEvent(ExpenseEvent.RECURRING_EXECUTED, r, created.id(), runDate);
            // Frontend status badge ("Chưa thực hiện" vs "Hoàn thành") is null-vs-not-null
            // on this field — set only once a transaction actually got created above.
            r.setLastRunDate(runDate);
            runDate = nextOccurrence(runDate, r);
            runs++;
        }
        r.setNextRunDate(runDate);
        if (r.getEndDate() != null && runDate.isAfter(r.getEndDate())) {
            r.setActive(false);
        }
        recurringTransactionDao.update(r);
    }

    /**
     * Validates the frequency-specific inputs and writes the whole schedule (frequency,
     * day fields, start/end and the derived nextRunDate). nextRunDate is re-derived from
     * scratch rather than preserved — editing a rule (e.g. fixing a typo'd amount)
     * shouldn't retroactively back-generate whatever the old schedule would have
     * produced between its old and new nextRunDate.
     */
    private void applySchedule(RecurringTransaction r, CreateRecurringTransactionRequest request) {
        String frequency = request.frequency() == null ? FREQUENCY_MONTHLY : request.frequency();
        switch (frequency) {
            case FREQUENCY_WEEKLY -> {
                if (request.dayOfWeek() == null) {
                    throw new BadRequestException("Giao dịch hàng tuần cần chọn thứ trong tuần");
                }
                r.setDayOfMonth(WEEKLY_PLACEHOLDER_DAY_OF_MONTH);
                r.setDayOfWeek(request.dayOfWeek());
                r.setMonthOfYear(null);
            }
            case FREQUENCY_YEARLY -> {
                if (request.monthOfYear() == null || request.dayOfMonth() == null) {
                    throw new BadRequestException("Giao dịch hàng năm cần chọn tháng và ngày trong tháng");
                }
                if (request.dayOfMonth() > Month.of(request.monthOfYear()).maxLength()) {
                    throw new BadRequestException(
                            "Tháng " + request.monthOfYear() + " không có ngày " + request.dayOfMonth());
                }
                r.setDayOfMonth(request.dayOfMonth());
                r.setDayOfWeek(null);
                r.setMonthOfYear(request.monthOfYear());
            }
            case FREQUENCY_MONTHLY -> {
                if (request.dayOfMonth() == null) {
                    throw new BadRequestException("Giao dịch hàng tháng cần chọn ngày trong tháng");
                }
                r.setDayOfMonth(request.dayOfMonth());
                r.setDayOfWeek(null);
                r.setMonthOfYear(null);
            }
            default -> throw new BadRequestException("Tần suất không hợp lệ: " + frequency);
        }
        r.setFrequency(frequency);
        r.setStartDate(request.startDate());
        r.setEndDate(request.endDate());
        r.setNextRunDate(firstOccurrenceOnOrAfter(r));
    }

    /**
     * A brand-new (or just-edited) rule's first run must never land in the past — a
     * {@code startDate} months behind "today" used to make {@link #generateDueTransactions}
     * treat it as a stale rule that missed runs, backfilling one transaction per missed
     * period the very first time the job saw it. {@code startDate} is still honored for a
     * genuinely future date (or today); a past one is simply floored to today so the rule
     * only starts generating from its next real occurrence onward.
     */
    private LocalDate firstOccurrenceOnOrAfter(RecurringTransaction r) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveStart = r.getStartDate().isBefore(today) ? today : r.getStartDate();
        return switch (frequencyOf(r)) {
            case FREQUENCY_WEEKLY -> effectiveStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(r.getDayOfWeek())));
            case FREQUENCY_YEARLY -> {
                LocalDate candidate = yearlyOccurrence(effectiveStart.getYear(), r);
                yield candidate.isBefore(effectiveStart) ? yearlyOccurrence(effectiveStart.getYear() + 1, r) : candidate;
            }
            default -> {
                LocalDate candidate = withClampedDay(effectiveStart, r.getDayOfMonth());
                yield candidate.isBefore(effectiveStart) ? nextOccurrence(effectiveStart, r) : candidate;
            }
        };
    }

    private LocalDate nextOccurrence(LocalDate from, RecurringTransaction r) {
        return switch (frequencyOf(r)) {
            case FREQUENCY_WEEKLY -> from.plusWeeks(1);
            case FREQUENCY_YEARLY -> yearlyOccurrence(from.getYear() + 1, r);
            default -> withClampedDay(from.plusMonths(1), r.getDayOfMonth());
        };
    }

    /** Null falls back to MONTHLY, the pre-V9 behaviour. */
    private static String frequencyOf(RecurringTransaction r) {
        return r.getFrequency() == null ? FREQUENCY_MONTHLY : r.getFrequency();
    }

    /** Feb 29 falls back to Feb 28 in a non-leap year. */
    private static LocalDate yearlyOccurrence(int year, RecurringTransaction r) {
        return withClampedDay(LocalDate.of(year, r.getMonthOfYear(), 1), r.getDayOfMonth());
    }

    /** Clamps to the last day of the month for a dayOfMonth beyond it (e.g. 31 in February). */
    private static LocalDate withClampedDay(LocalDate month, int dayOfMonth) {
        return month.withDayOfMonth(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private void requireCanModify(RecurringTransaction r, Long callerUserId, boolean callerIsOwner) {
        if (!callerIsOwner && !Objects.equals(r.getCreatedByUserId(), callerUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ hộ hoặc người tạo giao dịch định kỳ mới có quyền thực hiện thao tác này");
        }
    }

    private RecurringTransaction requireOwnedByFamily(Long id, Long familyId) {
        RecurringTransaction r = recurringTransactionDao.selectById(id)
                .orElseThrow(() -> new NotFoundException("Giao dịch định kỳ không tồn tại: " + id));
        if (!r.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Giao dịch định kỳ không tồn tại: " + id);
        }
        return r;
    }
}
