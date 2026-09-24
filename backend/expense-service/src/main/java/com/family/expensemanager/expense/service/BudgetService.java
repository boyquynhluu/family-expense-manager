package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CopyBudgetsRequest;
import com.family.expensemanager.expense.dto.CopyBudgetsResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "BudgetService")
public class BudgetService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BudgetDao budgetDao;
    private final CategoryService categoryService;

    @PreAuthorize("hasRole('OWNER')")
    public BudgetResponse create(Long familyId, CreateBudgetRequest request) {
        try {
            log.info("create - start, familyId={}, categoryId={}", familyId, request.categoryId());
            validateTarget(familyId, request, null);

            Budget budget = new Budget();
            budget.setFamilyId(familyId);
            budget.setCategoryId(request.categoryId());
            budget.setPeriodMonth(request.periodMonth());
            budget.setLimitAmount(request.limitAmount());
            budgetDao.insert(budget);
            return BudgetResponse.from(budget);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
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
            budget.setCategoryId(request.categoryId());
            budget.setPeriodMonth(request.periodMonth());
            budget.setLimitAmount(request.limitAmount());
            budgetDao.update(budget);
            return BudgetResponse.from(budget);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
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
            List<Budget> source = budgetDao.selectByFamilyAndPeriod(familyId, request.fromMonth());
            Set<Long> existingCategoryIds = new HashSet<>();
            for (Budget existing : budgetDao.selectByFamilyAndPeriod(familyId, request.toMonth())) {
                existingCategoryIds.add(existing.getCategoryId());
            }
            int copied = 0;
            for (Budget original : source) {
                if (!existingCategoryIds.add(original.getCategoryId())) {
                    continue;
                }
                Budget budget = new Budget();
                budget.setFamilyId(familyId);
                budget.setCategoryId(original.getCategoryId());
                budget.setPeriodMonth(request.toMonth());
                budget.setLimitAmount(original.getLimitAmount());
                budgetDao.insert(budget);
                copied++;
            }
            return new CopyBudgetsResponse(copied, source.size() - copied);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("BudgetService.delete", e);
        }
    }

    private void validateTarget(Long familyId, CreateBudgetRequest request, Long selfId) {
        Optional<Budget> duplicate;
        if (request.categoryId() == null) {
            duplicate = budgetDao.selectOverallByPeriod(familyId, request.periodMonth());
        } else {
            categoryService.requireOwnedByFamily(request.categoryId(), familyId, "EXPENSE");
            duplicate = budgetDao.selectByCategoryAndPeriod(request.categoryId(), request.periodMonth());
        }
        if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), selfId)) {
            throw logged(log, new BadRequestException(request.categoryId() == null
                    ? "Đã có ngân sách tổng chi tiêu cho tháng này"
                    : "Đã có ngân sách cho danh mục này trong tháng này"));
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
