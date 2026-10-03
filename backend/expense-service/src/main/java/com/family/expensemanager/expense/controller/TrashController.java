package com.family.expensemanager.expense.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.dto.TrashPurgeResult;
import com.family.expensemanager.expense.service.TrashService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Restoring stays on each resource ({@code POST /{kind}/{id}/restore}); deleting for good lives here.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/trash")
@RequiredArgsConstructor
@Slf4j(topic = "TrashController")
public class TrashController {

    private final TrashService trashService;

    @GetMapping("/count")
    public ApiResponse<TrashCountResponse> getTrash() { // { wallets, categories, transactions, total }
        return ApiResponse.ok(trashService.getTotalTrash(CurrentUser.familyId()));
    }

    @DeleteMapping("/transactions/{id}")
    public ApiResponse<Void> purgeTransaction(@PathVariable Long id) {
        log.info("purgeTransaction - start, id={}", id);
        trashService.purgeTransaction(CurrentUser.familyId(), id, CurrentUser.userId(), CurrentUser.displayName(),
                isOwner());
        return ApiResponse.ok();
    }

    @DeleteMapping("/wallets/{id}")
    public ApiResponse<Void> purgeWallet(@PathVariable Long id) {
        log.info("purgeWallet - start, id={}", id);
        trashService.purgeWallet(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> purgeCategory(@PathVariable Long id) {
        log.info("purgeCategory - start, id={}", id);
        trashService.purgeCategory(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }

    /** "Dọn sạch thùng rác" — OWNER only. */
    @DeleteMapping
    public ApiResponse<TrashPurgeResult> emptyTrash() {
        log.info("emptyTrash - start");
        return ApiResponse.ok(trashService.emptyTrash(CurrentUser.familyId(), CurrentUser.userId(),
                CurrentUser.displayName()));
    }

    private static boolean isOwner() {
        return "OWNER".equals(CurrentUser.role());
    }
}
