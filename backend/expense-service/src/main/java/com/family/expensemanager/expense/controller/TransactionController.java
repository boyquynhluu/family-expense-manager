package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import com.family.expensemanager.expense.service.TransactionService;
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
@RequestMapping("/api/expenses/transactions")
@RequiredArgsConstructor
@Slf4j(topic = "TransactionController")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        log.info("create - start");
        return ApiResponse.ok(transactionService.create(CurrentUser.familyId(), CurrentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<TransactionResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(transactionService.listByFamily(CurrentUser.familyId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> get(@PathVariable Long id) {
        log.info("get - start, id={}", id);
        return ApiResponse.ok(transactionService.get(CurrentUser.familyId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(transactionService.update(CurrentUser.familyId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        transactionService.delete(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }
}
