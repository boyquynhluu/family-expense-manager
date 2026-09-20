package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.expense.dto.WalletTransferResponse;
import com.family.expensemanager.expense.service.WalletTransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/transfers")
@RequiredArgsConstructor
@Slf4j(topic = "WalletTransferController")
public class WalletTransferController {

    private final WalletTransferService walletTransferService;

    @PostMapping
    public ApiResponse<WalletTransferResponse> create(@Valid @RequestBody CreateWalletTransferRequest request) {
        log.info("create - start");
        return ApiResponse.ok(walletTransferService.create(CurrentUser.familyId(), CurrentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<WalletTransferResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(walletTransferService.listByFamilyPaged(CurrentUser.familyId(), page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        walletTransferService.delete(CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.role(), id);
        return ApiResponse.ok();
    }
}
