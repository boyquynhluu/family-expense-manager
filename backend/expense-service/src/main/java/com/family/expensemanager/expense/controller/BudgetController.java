package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.BudgetResponse;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;
import com.family.expensemanager.expense.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PageResponse<BudgetResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(budgetService.listByFamilyPaged(CurrentUser.familyId(), page, size));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetResponse> update(@PathVariable Long id, @Valid @RequestBody CreateBudgetRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(budgetService.update(id, CurrentUser.familyId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        budgetService.delete(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }
}
