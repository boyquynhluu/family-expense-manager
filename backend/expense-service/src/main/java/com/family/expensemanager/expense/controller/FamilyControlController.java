package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.FamilySettingsRequest;
import com.family.expensemanager.expense.dto.FamilySettingsResponse;
import com.family.expensemanager.expense.dto.RejectApprovalRequest;
import com.family.expensemanager.expense.dto.SpendingLimitRequest;
import com.family.expensemanager.expense.dto.SpendingLimitResponse;
import com.family.expensemanager.expense.dto.TransactionApprovalResponse;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.service.SpendingLimitService;
import com.family.expensemanager.expense.service.TransactionApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * README A5: spending caps per member, the family's approval threshold, and the expenses waiting for approval.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j(topic = "FamilyControlController")
public class FamilyControlController {

    private final SpendingLimitService spendingLimitService;
    private final TransactionApprovalService approvalService;

    @GetMapping("/spending-limits")
    public ApiResponse<List<SpendingLimitResponse>> limits() {
        log.info("limits - start");
        return ApiResponse.ok(spendingLimitService.list(CurrentUser.familyId()));
    }

    @PutMapping("/spending-limits/{userId}")
    public ApiResponse<Void> setLimit(@PathVariable Long userId, @Valid @RequestBody SpendingLimitRequest request) {
        log.info("setLimit - start, userId={}", userId);
        spendingLimitService.set(CurrentUser.familyId(), userId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/family-settings")
    public ApiResponse<FamilySettingsResponse> settings() {
        return ApiResponse.ok(approvalService.settings(CurrentUser.familyId()));
    }

    @PutMapping("/family-settings")
    public ApiResponse<FamilySettingsResponse> updateSettings(@Valid @RequestBody FamilySettingsRequest request) {
        log.info("updateSettings - start");
        return ApiResponse.ok(approvalService.updateSettings(CurrentUser.familyId(), request));
    }

    @GetMapping("/approvals")
    public ApiResponse<PageResponse<TransactionApprovalResponse>> approvals(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("approvals - start, page={}, size={}", page, size);
        return ApiResponse.ok(approvalService.list(CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.role(),
                page, size));
    }

    @GetMapping("/approvals/pending-count")
    public ApiResponse<TrashCountResponse> pendingCount() {
        return ApiResponse.ok(new TrashCountResponse(approvalService.countPending(CurrentUser.familyId())));
    }

    @PostMapping("/approvals/{id}/approve")
    public ApiResponse<TransactionApprovalResponse> approve(@PathVariable Long id) {
        log.info("approve - start, id={}", id);
        return ApiResponse.ok(approvalService.approve(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.displayName()));
    }

    @PostMapping("/approvals/{id}/reject")
    public ApiResponse<TransactionApprovalResponse> reject(@PathVariable Long id,
                                                           @Valid @RequestBody(required = false) RejectApprovalRequest request) {
        log.info("reject - start, id={}", id);
        return ApiResponse.ok(approvalService.reject(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.displayName(), request == null ? null : request.reason()));
    }
}
