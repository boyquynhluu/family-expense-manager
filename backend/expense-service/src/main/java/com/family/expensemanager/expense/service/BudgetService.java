package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "BudgetService")
public class BudgetService {

    private final BudgetDao budgetDao;
    private final CategoryService categoryService;

    @Transactional
    public BudgetResponse create(Long familyId, CreateBudgetRequest request) {
        log.info("create - start, familyId={}, categoryId={}", familyId, request.categoryId());
        categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        Budget budget = new Budget();
        budget.setFamilyId(familyId);
        budget.setCategoryId(request.categoryId());
        budget.setPeriodMonth(request.periodMonth());
        budget.setLimitAmount(request.limitAmount());
        budgetDao.insert(budget);
        return BudgetResponse.from(budget);
    }

    public List<BudgetResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return budgetDao.selectByFamilyId(familyId).stream().map(BudgetResponse::from).toList();
    }

    @Transactional
    public BudgetResponse update(Long budgetId, Long familyId, CreateBudgetRequest request) {
        log.info("update - start, budgetId={}, familyId={}", budgetId, familyId);
        Budget budget = requireOwnedByFamily(budgetId, familyId);
        categoryService.requireOwnedByFamily(request.categoryId(), familyId);
        budget.setCategoryId(request.categoryId());
        budget.setPeriodMonth(request.periodMonth());
        budget.setLimitAmount(request.limitAmount());
        budgetDao.update(budget);
        return BudgetResponse.from(budget);
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long budgetId, Long familyId) {
        log.info("delete - start, budgetId={}, familyId={}", budgetId, familyId);
        Budget budget = requireOwnedByFamily(budgetId, familyId);
        budgetDao.delete(budget);
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
