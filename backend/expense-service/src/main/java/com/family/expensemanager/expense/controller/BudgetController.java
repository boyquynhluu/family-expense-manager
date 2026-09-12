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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/budgets")
@RequiredArgsConstructor
@Slf4j(topic = "BudgetController")
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ApiResponse<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        log.info("create - start");
        return ApiResponse.ok(budgetService.create(CurrentUser.familyId(), request));
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(budgetService.listByFamily(CurrentUser.familyId()));
    }
}
