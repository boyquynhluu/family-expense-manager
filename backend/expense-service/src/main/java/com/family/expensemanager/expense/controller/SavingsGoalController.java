package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.SavingsGoalRequest;
import com.family.expensemanager.expense.dto.SavingsGoalResponse;
import com.family.expensemanager.expense.service.SavingsGoalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * README C1 "Mục tiêu tiết kiệm".
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/savings-goals")
@RequiredArgsConstructor
@Slf4j(topic = "SavingsGoalController")
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public ApiResponse<List<SavingsGoalResponse>> list() {
        return ApiResponse.ok(savingsGoalService.list(CurrentUser.familyId()));
    }

    @PostMapping
    public ApiResponse<SavingsGoalResponse> create(@Valid @RequestBody SavingsGoalRequest request) {
        log.info("create - start");
        return ApiResponse.ok(savingsGoalService.create(CurrentUser.familyId(), CurrentUser.userId(),
                CurrentUser.displayName(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SavingsGoalResponse> update(@PathVariable Long id, @Valid @RequestBody SavingsGoalRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(savingsGoalService.update(CurrentUser.familyId(), id, CurrentUser.userId(), isOwner(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        savingsGoalService.delete(CurrentUser.familyId(), id, CurrentUser.userId(), isOwner());
        return ApiResponse.ok();
    }

    private static boolean isOwner() {
        return "OWNER".equals(CurrentUser.role());
    }
}
