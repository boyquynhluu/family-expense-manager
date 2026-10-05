package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.WalletAdjustmentRequest;
import com.family.expensemanager.expense.dto.WalletAdjustmentResponse;
import com.family.expensemanager.expense.service.WalletAdjustmentService;
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

/**
 * "Điều chỉnh số dư" (README B1).
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/wallet-adjustments")
@RequiredArgsConstructor
@Slf4j(topic = "WalletAdjustmentController")
public class WalletAdjustmentController {

    private static final String ROLE_OWNER = "OWNER";

    private final WalletAdjustmentService walletAdjustmentService;

    @PostMapping
    public ApiResponse<WalletAdjustmentResponse> create(@Valid @RequestBody WalletAdjustmentRequest request) {
        log.info("create - start");
        return ApiResponse.ok(walletAdjustmentService.create(
                CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.displayName(), isOwner(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<WalletAdjustmentResponse>> list(
            @RequestParam(required = false) Long walletId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, walletId={}, page={}, size={}", walletId, page, size);
        return ApiResponse.ok(walletAdjustmentService.listPaged(CurrentUser.familyId(), walletId, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        walletAdjustmentService.delete(CurrentUser.familyId(), id, CurrentUser.userId(), isOwner());
        return ApiResponse.ok();
    }

    private static boolean isOwner() {
        return ROLE_OWNER.equals(CurrentUser.role());
    }
}
