package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.dto.CreateRecurringTransactionRequest;
import com.family.expensemanager.expense.dto.RecurringTransactionResponse;
import com.family.expensemanager.expense.dto.TransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final Clock clock;

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
        r.setDayOfMonth(request.dayOfMonth());
        r.setStartDate(request.startDate());
        r.setEndDate(request.endDate());
        r.setNextRunDate(firstOccurrenceOnOrAfter(request.startDate(), request.dayOfMonth()));
        r.setActive(true);
        r.setCreatedAt(LocalDateTime.now(clock));

        recurringTransactionDao.insert(r);
        return RecurringTransactionResponse.from(r);
    }

    public List<RecurringTransactionResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return recurringTransactionDao.selectByFamilyId(familyId).stream()
                .map(RecurringTransactionResponse::from)
                .toList();
    }

    @Transactional
    public RecurringTransactionResponse update(
            Long id, Long familyId, CreateRecurringTransactionRequest request) {
        log.info("update - start, id={}, familyId={}", id, familyId);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
        walletService.requireOwnedByFamily(request.walletId(), familyId);
        categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        r.setWalletId(request.walletId());
        r.setCategoryId(request.categoryId());
        r.setType(request.type());
        r.setAmount(request.amount());
        r.setNote(request.note());
        r.setDayOfMonth(request.dayOfMonth());
        r.setStartDate(request.startDate());
        r.setEndDate(request.endDate());
        // Re-derived from scratch rather than preserved — editing a rule (e.g. fixing a
        // typo'd amount) shouldn't retroactively back-generate whatever the old
        // schedule would have produced between its old and new nextRunDate.
        r.setNextRunDate(firstOccurrenceOnOrAfter(request.startDate(), request.dayOfMonth()));
        recurringTransactionDao.update(r);
        return RecurringTransactionResponse.from(r);
    }

    @Transactional
    public void setActive(Long id, Long familyId, boolean active) {
        log.info("setActive - start, id={}, familyId={}, active={}", id, familyId, active);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
        r.setActive(active);
        recurringTransactionDao.update(r);
    }

    @Transactional
    public void delete(Long id, Long familyId) {
        log.info("delete - start, id={}, familyId={}", id, familyId);
        RecurringTransaction r = requireOwnedByFamily(id, familyId);
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
            }
        }
    }

    private void processDueRule(RecurringTransaction r, LocalDate today) {
        LocalDate runDate = r.getNextRunDate();
        int runs = 0;
        while (!runDate.isAfter(today) && runs < MAX_CATCH_UP_RUNS) {
            TransactionRequest request = new TransactionRequest(
                    r.getWalletId(), r.getCategoryId(), r.getType(), r.getAmount(), runDate.atStartOfDay(),
                    r.getNote());
            transactionService.create(
                    r.getFamilyId(), r.getCreatedByUserId(), r.getCreatedByEmail(), r.getCreatedByDisplayName(),
                    request);
            runDate = nextOccurrence(runDate, r.getDayOfMonth());
            runs++;
        }
        r.setNextRunDate(runDate);
        if (r.getEndDate() != null && runDate.isAfter(r.getEndDate())) {
            r.setActive(false);
        }
        recurringTransactionDao.update(r);
    }

    private LocalDate firstOccurrenceOnOrAfter(LocalDate startDate, int dayOfMonth) {
        LocalDate candidate = withClampedDay(startDate, dayOfMonth);
        return candidate.isBefore(startDate) ? nextOccurrence(startDate, dayOfMonth) : candidate;
    }

    private LocalDate nextOccurrence(LocalDate from, int dayOfMonth) {
        return withClampedDay(from.plusMonths(1), dayOfMonth);
    }

    /** Clamps to the last day of the month for a dayOfMonth beyond it (e.g. 31 in February). */
    private LocalDate withClampedDay(LocalDate month, int dayOfMonth) {
        return month.withDayOfMonth(Math.min(dayOfMonth, month.lengthOfMonth()));
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
