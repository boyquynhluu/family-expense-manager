package com.family.expensemanager.expense.service;

import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BudgetService {

    private final BudgetDao budgetDao;
    private final CategoryService categoryService;

    public BudgetService(BudgetDao budgetDao, CategoryService categoryService) {
        this.budgetDao = budgetDao;
        this.categoryService = categoryService;
    }

    @Transactional
    public BudgetResponse create(Long familyId, CreateBudgetRequest request) {
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
        return budgetDao.selectByFamilyId(familyId).stream().map(BudgetResponse::from).toList();
    }
}
