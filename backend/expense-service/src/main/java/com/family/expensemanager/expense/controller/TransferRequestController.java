package com.family.expensemanager.expense.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CreateTransferRequestRequest;
import com.family.expensemanager.expense.dto.TransferRequestResponse;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.service.TransferRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Yêu cầu chuyển tiền" — see {@link TransferRequestService}.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/transfer-requests")
@RequiredArgsConstructor
@Slf4j(topic = "TransferRequestController")
public class TransferRequestController {

    private final TransferRequestService transferRequestService;

    @PostMapping
    public ApiResponse<TransferRequestResponse> create(@Valid @RequestBody CreateTransferRequestRequest request) {
        log.info("create - start");
        return ApiResponse.ok(transferRequestService.create(
                CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.displayName(), request));
    }

    /** Requests the caller sent, and requests waiting for the caller's decision. */
    @GetMapping
    public ApiResponse<PageResponse<TransferRequestResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(transferRequestService.listForUser(CurrentUser.familyId(), CurrentUser.userId(), page, size));
    }

    @GetMapping("/pending-count")
    public ApiResponse<TrashCountResponse> pendingCount() {
        return ApiResponse.ok(transferRequestService.countPendingForApprover(CurrentUser.familyId(), CurrentUser.userId()));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<TransferRequestResponse> approve(@PathVariable Long id) {
        log.info("approve - start, id={}", id);
        return ApiResponse.ok(transferRequestService.approve(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.email(), CurrentUser.displayName(), CurrentUser.role()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<TransferRequestResponse> reject(@PathVariable Long id) {
        log.info("reject - start, id={}", id);
        return ApiResponse.ok(transferRequestService.reject(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.displayName()));
    }
}
