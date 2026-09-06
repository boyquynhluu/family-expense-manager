package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import com.family.expensemanager.expense.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/expenses/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ApiResponse<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        return ApiResponse.ok(walletService.create(CurrentUser.familyId(), request));
    }

    @GetMapping
    public ApiResponse<List<WalletResponse>> list() {
        return ApiResponse.ok(walletService.listByFamily(CurrentUser.familyId()));
    }
}
