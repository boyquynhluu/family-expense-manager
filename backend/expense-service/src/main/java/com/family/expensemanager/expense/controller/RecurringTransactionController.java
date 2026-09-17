package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CreateRecurringTransactionRequest;
import com.family.expensemanager.expense.dto.RecurringTransactionResponse;
import com.family.expensemanager.expense.dto.SetActiveRequest;
import com.family.expensemanager.expense.service.RecurringTransactionService;
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

@RestController
@RequestMapping("/api/expenses/recurring-transactions")
@RequiredArgsConstructor
@Slf4j(topic = "RecurringTransactionController")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;

    @PostMapping
    public ApiResponse<RecurringTransactionResponse> create(
            @Valid @RequestBody CreateRecurringTransactionRequest request) {
        log.info("create - start");
        return ApiResponse.ok(recurringTransactionService.create(
                CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.email(), CurrentUser.displayName(),
                request));
    }

    @GetMapping
    public ApiResponse<List<RecurringTransactionResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(recurringTransactionService.listByFamily(CurrentUser.familyId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecurringTransactionResponse> update(
            @PathVariable Long id, @Valid @RequestBody CreateRecurringTransactionRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(recurringTransactionService.update(id, CurrentUser.familyId(), request));
    }

    @PutMapping("/{id}/active")
    public ApiResponse<Void> setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        log.info("setActive - start, id={}", id);
        recurringTransactionService.setActive(id, CurrentUser.familyId(), request.active());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        recurringTransactionService.delete(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }
}
