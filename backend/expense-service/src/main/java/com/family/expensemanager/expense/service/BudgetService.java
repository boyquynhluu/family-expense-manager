package com.family.expensemanager.expense.service;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.seasar.doma.jdbc.OptimisticLockException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.BudgetStatusResponse;
import com.family.expensemanager.expense.dto.CopyBudgetsRequest;
import com.family.expensemanager.expense.dto.CopyBudgetsResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "BudgetService")
public class BudgetService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MIN_AMOUNT_BUDGET = 10_000L;
    private static final long MAX_AMOUNT_BUDGET = 5_000_000L;

    private final BudgetDao budgetDao;
    private final CategoryService categoryService;
    private final WalletService walletService;
    private final BudgetMonitor budgetMonitor;
    private final EntityAuditService entityAuditService;

    @PreAuthorize("hasRole('OWNER')")
    public BudgetResponse create(Long familyId, CreateBudgetRequest request) {
        try {
            log.info("create - start, familyId={}, categoryId={}", familyId, request.categoryId());
            validateTarget(familyId, request, null);

            Budget budget = new Budget();
            budget.setFamilyId(familyId);
            apply(budget, request);
            budgetDao.insert(budget);
            BudgetResponse response = BudgetResponse.from(budget);
            entityAuditService.record(familyId, EntityAuditService.BUDGET, budget.getId(), EntityAuditService.ACTION_CREATED,
                    null, response);
            return response;
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.create", e);
        }
    }

    public PageResponse<BudgetResponse> listByFamilyPaged(Long familyId, int page, int size) {
        try {
            log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = budgetDao.countByFamilyId(familyId);
            List<BudgetResponse> content = budgetDao.selectByFamilyIdPaged(familyId, size, page * size).stream()
                    .map(BudgetResponse::from)
                    .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.listByFamilyPaged", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public BudgetResponse update(Long budgetId, Long familyId, CreateBudgetRequest request) {
        try {
            log.info("update - start, budgetId={}, familyId={}", budgetId, familyId);
            Budget budget = requireOwnedByFamily(budgetId, familyId);
            validateTarget(familyId, request, budgetId);
            BudgetResponse before = BudgetResponse.from(budget);
            apply(budget, request);
            budgetDao.update(budget);
            BudgetResponse after = BudgetResponse.from(budget);
            entityAuditService.record(familyId, EntityAuditService.BUDGET, budgetId, EntityAuditService.ACTION_UPDATED,
                    before, after);
            return after;
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.update", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public CopyBudgetsResponse copy(Long familyId, CopyBudgetsRequest request) {
        try {
            log.info("copy - start, familyId={}, fromMonth={}, toMonth={}", familyId, request.fromMonth(),
                    request.toMonth());
            if (request.fromMonth().equals(request.toMonth())) {
                throw logged(log, new BadRequestException("Tháng nguồn và tháng đích phải khác nhau"));
            }
            // Monthly budgets only (a yearly one has no "month" to copy to); a duplicate = same full scope (A6).
            List<Budget> source = budgetDao.selectByFamilyAndPeriod(familyId, request.fromMonth()).stream()
                    .filter(b -> !BudgetMonitor.PERIOD_YEAR.equals(b.getPeriodType()))
                    .toList();
            Set<List<Object>> existingScopes = new HashSet<>();
            for (Budget existing : budgetDao.selectByFamilyAndPeriod(familyId, request.toMonth())) {
                existingScopes.add(scopeKey(existing));
            }
            int copied = 0;
            for (Budget original : source) {
                if (!existingScopes.add(scopeKey(original))) {
                    continue;
                }
                Budget budget = new Budget();
                budget.setFamilyId(familyId);
                budget.setCategoryId(original.getCategoryId());
                budget.setWalletId(original.getWalletId());
                budget.setUserId(original.getUserId());
                budget.setPeriodType(BudgetMonitor.PERIOD_MONTH);
                budget.setRollover(original.getRollover());
                budget.setPeriodMonth(request.toMonth());
                budget.setLimitAmount(original.getLimitAmount());
                budgetDao.insert(budget);
                copied++;
            }
            return new CopyBudgetsResponse(copied, source.size() - copied);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.copy", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long budgetId, Long familyId) {
        try {
            log.info("delete - start, budgetId={}, familyId={}", budgetId, familyId);
            Budget budget = requireOwnedByFamily(budgetId, familyId);
            budgetDao.delete(budget);
            entityAuditService.record(familyId, EntityAuditService.BUDGET, budgetId, EntityAuditService.ACTION_DELETED,
                    BudgetResponse.from(budget), null);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.delete", e);
        }
    }

    /** README A6: every budget covering that month (monthly ones plus the year's yearly ones), with spend and rollover. */
    public List<BudgetStatusResponse> status(Long familyId, String yearMonth) {
        try {
            log.info("status - start, familyId={}, yearMonth={}", familyId, yearMonth);
            return budgetMonitor.status(familyId, java.time.YearMonth.parse(yearMonth));
        } catch (java.time.format.DateTimeParseException e) {
            throw logged(log, new BadRequestException("Tháng không hợp lệ (định dạng yyyy-MM)"));
        }
    }

    private void apply(Budget budget, CreateBudgetRequest request) {
        budget.setCategoryId(request.categoryId());
        budget.setWalletId(request.walletId());
        budget.setUserId(request.userId());
        budget.setPeriodType(periodTypeOf(request));
        budget.setPeriodMonth(request.periodMonth());
        budget.setLimitAmount(request.limitAmount());
        budget.setRollover(Boolean.TRUE.equals(request.rollover()));
    }

    private static String periodTypeOf(CreateBudgetRequest request) {
        return request.periodType() == null ? BudgetMonitor.PERIOD_MONTH : request.periodType();
    }

    private static List<Object> scopeKey(Budget b) {
        return java.util.Arrays.asList(b.getCategoryId(), b.getWalletId(), b.getUserId());
    }

    private void validateTarget(Long familyId, CreateBudgetRequest request, Long selfId) {
        String periodType = periodTypeOf(request);
        boolean yearly = BudgetMonitor.PERIOD_YEAR.equals(periodType);
        if (yearly != (request.periodMonth().length() == 4)) {
            throw logged(log, new BadRequestException(yearly
                    ? "Ngân sách theo năm cần kỳ dạng yyyy" : "Ngân sách theo tháng cần kỳ dạng yyyy-MM"));
        }
        // A yearly budget covers twelve months, so its cap scales with it.
        long max = yearly ? MAX_AMOUNT_BUDGET * 12 : MAX_AMOUNT_BUDGET;
        if(request.limitAmount().compareTo(BigDecimal.valueOf(MIN_AMOUNT_BUDGET)) < 0) {
            throw logged(log, new BadRequestException("Hạn mức không được dưới 10.000 đ"));
        }
        if(request.limitAmount().compareTo(BigDecimal.valueOf(max)) > 0) {
            throw logged(log, new BadRequestException("Hạn mức không được vượt quá "
                    + com.family.expensemanager.common.currency.CurrencyUtil.formatCurrency(BigDecimal.valueOf(max))));
        }
        if (request.categoryId() != null) {
            categoryService.requireOwnedByFamily(request.categoryId(), familyId, "EXPENSE");
        }
        if (request.walletId() != null) {
            walletService.requireOwnedByFamily(request.walletId(), familyId);
        }
        Optional<Budget> duplicate = budgetDao.selectSameScope(familyId, request.categoryId(), request.walletId(),
                request.userId(), periodType, request.periodMonth());
        if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), selfId)) {
            throw logged(log, new BadRequestException(request.categoryId() == null
                    ? "Đã có ngân sách tổng chi tiêu với phạm vi này cho kỳ này"
                    : "Đã có ngân sách cho danh mục này với phạm vi này trong kỳ này"));
        }
    }

    private Budget requireOwnedByFamily(Long budgetId, Long familyId) {
        Budget budget = budgetDao.selectById(budgetId)
                .orElseThrow(() -> logged(log, new NotFoundException("Ngân sách không tồn tại: " + budgetId)));
        if (!budget.getFamilyId().equals(familyId)) {
            throw logged(log, new NotFoundException("Ngân sách không tồn tại: " + budgetId));
        }
        return budget;
    }
}
