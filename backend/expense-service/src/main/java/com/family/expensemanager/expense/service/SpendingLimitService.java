package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.MemberSpendingLimitDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.MemberSpendingLimit;
import com.family.expensemanager.expense.dto.SpendingLimitRequest;
import com.family.expensemanager.expense.dto.SpendingLimitResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README A5: caps on what one member (typically a CHILD) may spend per day and per month. Checked whenever that
 * member records or edits an expense themselves — not for the OWNER, nor for what the OWNER approves for them.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "SpendingLimitService")
public class SpendingLimitService {

    private final MemberSpendingLimitDao limitDao;
    private final TransactionDao transactionDao;
    private final Clock clock;

    /**
     * Throws 400 when adding {@code amount} on {@code occurredAt}'s day (or month) would pass the member's cap.
     * {@code excludeTransactionId}: the transaction being edited (its old amount must not count twice).
     */
    public void requireWithinLimits(Long familyId, Long userId, BigDecimal amount, LocalDateTime occurredAt,
                                    Long excludeTransactionId) {
        MemberSpendingLimit limit = limitDao.selectByFamilyAndUser(familyId, userId).orElse(null);
        if (limit == null || amount == null || occurredAt == null) {
            return;
        }
        LocalDate day = occurredAt.toLocalDate();
        if (limit.getDailyLimit() != null) {
            BigDecimal spent = transactionDao.sumUserExpenseBetween(familyId, userId, day, day.plusDays(1),
                    excludeTransactionId);
            requireWithin(spent, amount, limit.getDailyLimit(), "ngày " + day);
        }
        if (limit.getMonthlyLimit() != null) {
            YearMonth month = YearMonth.from(day);
            BigDecimal spent = transactionDao.sumUserExpenseBetween(familyId, userId, month.atDay(1),
                    month.plusMonths(1).atDay(1), excludeTransactionId);
            requireWithin(spent, amount, limit.getMonthlyLimit(), "tháng " + month);
        }
    }

    private void requireWithin(BigDecimal spent, BigDecimal amount, BigDecimal cap, String period) {
        if (spent.add(amount).compareTo(cap) > 0) {
            BigDecimal left = cap.subtract(spent).max(BigDecimal.ZERO);
            throw logged(log, new BadRequestException("Vượt hạn mức chi tiêu " + period + ": hạn mức "
                    + CurrencyUtil.formatCurrency(cap) + ", còn lại " + CurrencyUtil.formatCurrency(left)));
        }
    }

    public List<SpendingLimitResponse> list(Long familyId) {
        try {
            log.info("list - start, familyId={}", familyId);
            LocalDate today = LocalDate.now(clock);
            YearMonth month = YearMonth.from(today);
            return limitDao.selectByFamilyId(familyId).stream()
                    .map(l -> new SpendingLimitResponse(l.getUserId(), l.getDailyLimit(), l.getMonthlyLimit(),
                            transactionDao.sumUserExpenseBetween(familyId, l.getUserId(), today, today.plusDays(1), null),
                            transactionDao.sumUserExpenseBetween(familyId, l.getUserId(), month.atDay(1),
                                    month.plusMonths(1).atDay(1), null)))
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SpendingLimitService.list", e);
        }
    }

    /** Both limits null removes the member's row. */
    @PreAuthorize("hasRole('OWNER')")
    public void set(Long familyId, Long userId, SpendingLimitRequest request) {
        try {
            log.info("set - start, familyId={}, userId={}", familyId, userId);
            if (request.dailyLimit() != null && request.monthlyLimit() != null
                    && request.dailyLimit().compareTo(request.monthlyLimit()) > 0) {
                throw logged(log, new BadRequestException("Hạn mức ngày không được lớn hơn hạn mức tháng"));
            }
            MemberSpendingLimit existing = limitDao.selectByFamilyAndUser(familyId, userId).orElse(null);
            if (request.dailyLimit() == null && request.monthlyLimit() == null) {
                if (existing != null) {
                    limitDao.delete(existing);
                }
                return;
            }
            MemberSpendingLimit limit = existing != null ? existing : new MemberSpendingLimit();
            limit.setFamilyId(familyId);
            limit.setUserId(userId);
            limit.setDailyLimit(request.dailyLimit());
            limit.setMonthlyLimit(request.monthlyLimit());
            limit.setUpdatedAt(LocalDateTime.now(clock));
            if (existing != null) {
                limitDao.update(limit);
            } else {
                limitDao.insert(limit);
            }
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SpendingLimitService.set", e);
        }
    }
}
