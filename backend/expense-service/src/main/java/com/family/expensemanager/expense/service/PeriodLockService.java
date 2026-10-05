package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.PeriodLockDao;
import com.family.expensemanager.expense.dao.PeriodLockLogDao;
import com.family.expensemanager.expense.domain.entity.PeriodLock;
import com.family.expensemanager.expense.domain.entity.PeriodLockLog;
import com.family.expensemanager.expense.dto.PeriodLockLogResponse;
import com.family.expensemanager.expense.dto.PeriodLockResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * "Chốt sổ theo tháng" (README B2): the family OWNER closes a settled month, after which nothing dated in it
 * may be created, edited, deleted or restored — transactions, transfers, balance adjustments, import rows
 * (see {@link #requireUnlocked}) — so that month's balances and budget results stop changing behind anyone's
 * back. Only past months can be closed (the current one is still being written into); only the OWNER may
 * reopen one, and every lock/unlock is kept in PERIOD_LOCK_LOGS.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "PeriodLockService")
public class PeriodLockService {

    static final String ACTION_LOCKED = "LOCKED";
    static final String ACTION_UNLOCKED = "UNLOCKED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final PeriodLockDao periodLockDao;
    private final PeriodLockLogDao periodLockLogDao;
    private final Clock clock;

    /**
     * Throws 409 if any of {@code dates} falls in a closed month — call it with every date a write touches
     * (for an edit: the old AND the new date, so nothing can be moved into or out of a closed month).
     * Null dates are ignored.
     */
    public void requireUnlocked(Long familyId, LocalDateTime... dates) {
        List<String> months = Arrays.stream(dates)
                .filter(Objects::nonNull)
                .map(d -> YearMonth.from(d).toString())
                .distinct()
                .toList();
        if (months.isEmpty() || periodLockDao.countByFamilyAndMonths(familyId, months) == 0) {
            return;
        }
        String closed = months.stream()
                .filter(m -> periodLockDao.selectByFamilyAndMonth(familyId, m).isPresent())
                .findFirst()
                .orElse(months.get(0));
        throw logged(log, new ConflictException("Tháng " + closed
                + " đã chốt sổ — không thể thêm, sửa, xoá hay khôi phục dữ liệu của tháng này. Chủ hộ có thể mở lại sổ nếu cần"));
    }

    /** For callers that must skip rather than fail (the recurring-transaction scheduler). */
    public boolean isLocked(Long familyId, LocalDate date) {
        return periodLockDao.selectByFamilyAndMonth(familyId, YearMonth.from(date).toString()).isPresent();
    }

    /** Whether the family has closed any month at all (changing a wallet's initial balance is then refused). */
    public boolean hasAnyLock(Long familyId) {
        return periodLockDao.countByFamilyId(familyId) > 0;
    }

    public List<PeriodLockResponse> list(Long familyId) {
        try {
            log.info("list - start, familyId={}", familyId);
            return periodLockDao.selectByFamilyId(familyId).stream().map(PeriodLockResponse::from).toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("PeriodLockService.list", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public PeriodLockResponse lock(Long familyId, String periodMonth, Long userId, String userName) {
        try {
            log.info("lock - start, familyId={}, periodMonth={}", familyId, periodMonth);
            YearMonth month = parseMonth(periodMonth);
            if (!month.isBefore(YearMonth.now(clock))) {
                throw logged(log, new BadRequestException("Chỉ chốt sổ được các tháng đã qua (trước tháng "
                        + YearMonth.now(clock) + ")"));
            }
            String key = month.toString();
            if (periodLockDao.selectByFamilyAndMonth(familyId, key).isPresent()) {
                throw logged(log, new ConflictException("Tháng " + key + " đã được chốt sổ"));
            }
            PeriodLock lock = new PeriodLock();
            lock.setFamilyId(familyId);
            lock.setPeriodMonth(key);
            lock.setLockedByUserId(userId);
            lock.setLockedByName(truncateName(userName));
            lock.setLockedAt(LocalDateTime.now(clock));
            periodLockDao.insert(lock);
            writeLog(familyId, key, ACTION_LOCKED, userId, userName);
            return PeriodLockResponse.from(lock);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("PeriodLockService.lock", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void unlock(Long familyId, String periodMonth, Long userId, String userName) {
        try {
            log.info("unlock - start, familyId={}, periodMonth={}", familyId, periodMonth);
            String key = parseMonth(periodMonth).toString();
            PeriodLock lock = periodLockDao.selectByFamilyAndMonth(familyId, key)
                    .orElseThrow(() -> logged(log, new NotFoundException("Tháng " + key + " chưa được chốt sổ")));
            periodLockDao.delete(lock);
            writeLog(familyId, key, ACTION_UNLOCKED, userId, userName);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("PeriodLockService.unlock", e);
        }
    }

    public PageResponse<PeriodLockLogResponse> history(Long familyId, int page, int size) {
        try {
            log.info("history - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long total = periodLockLogDao.countByFamilyId(familyId);
            List<PeriodLockLogResponse> content = periodLockLogDao.selectByFamilyIdPaged(familyId, size, page * size)
                    .stream()
                    .map(PeriodLockLogResponse::from)
                    .toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("PeriodLockService.history", e);
        }
    }

    private void writeLog(Long familyId, String periodMonth, String action, Long userId, String userName) {
        PeriodLockLog entry = new PeriodLockLog();
        entry.setFamilyId(familyId);
        entry.setPeriodMonth(periodMonth);
        entry.setAction(action);
        entry.setActorUserId(userId);
        entry.setActorName(truncateName(userName));
        entry.setCreatedAt(LocalDateTime.now(clock));
        periodLockLogDao.insert(entry);
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw logged(log, new BadRequestException("Tháng không hợp lệ (định dạng yyyy-MM)"));
        }
    }

    private static String truncateName(String name) {
        return name != null && name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
