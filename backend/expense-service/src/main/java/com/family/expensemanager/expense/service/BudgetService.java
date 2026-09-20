package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CopyBudgetsRequest;
import com.family.expensemanager.expense.dto.CopyBudgetsResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j(topic = "BudgetService")
public class BudgetService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BudgetDao budgetDao;
    private final CategoryService categoryService;

    @Transactional
    public BudgetResponse create(Long familyId, CreateBudgetRequest request) {
        log.info("create - start, familyId={}, categoryId={}", familyId, request.categoryId());
        validateTarget(familyId, request, null);

        Budget budget = new Budget();
        budget.setFamilyId(familyId);
        budget.setCategoryId(request.categoryId());
        budget.setPeriodMonth(request.periodMonth());
        budget.setLimitAmount(request.limitAmount());
        budgetDao.insert(budget);
        return BudgetResponse.from(budget);
    }

    public PageResponse<BudgetResponse> listByFamilyPaged(Long familyId, int page, int size) {
        log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = budgetDao.countByFamilyId(familyId);
        List<BudgetResponse> content = budgetDao.selectByFamilyIdPaged(familyId, size, page * size).stream()
                .map(BudgetResponse::from)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    public BudgetResponse update(Long budgetId, Long familyId, CreateBudgetRequest request) {
        log.info("update - start, budgetId={}, familyId={}", budgetId, familyId);
        Budget budget = requireOwnedByFamily(budgetId, familyId);
        validateTarget(familyId, request, budgetId);
        budget.setCategoryId(request.categoryId());
        budget.setPeriodMonth(request.periodMonth());
        budget.setLimitAmount(request.limitAmount());
        budgetDao.update(budget);
        return BudgetResponse.from(budget);
    }

    @Transactional
    public CopyBudgetsResponse copy(Long familyId, CopyBudgetsRequest request) {
        log.info("copy - start, familyId={}, fromMonth={}, toMonth={}", familyId, request.fromMonth(),
                request.toMonth());
        if (request.fromMonth().equals(request.toMonth())) {
            throw new BadRequestException("Tháng nguồn và tháng đích phải khác nhau");
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
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long budgetId, Long familyId) {
        log.info("delete - start, budgetId={}, familyId={}", budgetId, familyId);
        Budget budget = requireOwnedByFamily(budgetId, familyId);
        budgetDao.delete(budget);
    }

    private void validateTarget(Long familyId, CreateBudgetRequest request, Long selfId) {
        Optional<Budget> duplicate;
        if (request.categoryId() == null) {
            duplicate = budgetDao.selectOverallByPeriod(familyId, request.periodMonth());
        } else {
            categoryService.requireOwnedByFamily(request.categoryId(), familyId);
            duplicate = budgetDao.selectByCategoryAndPeriod(request.categoryId(), request.periodMonth());
        }
        if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), selfId)) {
            throw new BadRequestException(request.categoryId() == null
                    ? "Đã có ngân sách tổng chi tiêu cho tháng này"
                    : "Đã có ngân sách cho danh mục này trong tháng này");
        }
    }

    private Budget requireOwnedByFamily(Long budgetId, Long familyId) {
        Budget budget = budgetDao.selectById(budgetId)
                .orElseThrow(() -> new NotFoundException("Ngân sách không tồn tại: " + budgetId));
        if (!budget.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Ngân sách không tồn tại: " + budgetId);
        }
        return budget;
    }
}
