package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
import com.family.expensemanager.expense.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/expenses/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ApiResponse<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        return ApiResponse.ok(budgetService.create(CurrentUser.familyId(), request));
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> list() {
        return ApiResponse.ok(budgetService.listByFamily(CurrentUser.familyId()));
    }
}
