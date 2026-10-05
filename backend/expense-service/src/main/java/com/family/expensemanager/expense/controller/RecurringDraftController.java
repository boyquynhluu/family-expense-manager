package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.ConfirmDraftRequest;
import com.family.expensemanager.expense.dto.RecurringDraftResponse;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.service.RecurringDraftService;
import jakarta.validation.Valid;
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
 * README A4: occurrences of CONFIRM-mode recurring rules waiting for their real amount.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/recurring-drafts")
@RequiredArgsConstructor
@Slf4j(topic = "RecurringDraftController")
public class RecurringDraftController {

    private final RecurringDraftService recurringDraftService;

    @GetMapping
    public ApiResponse<PageResponse<RecurringDraftResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(recurringDraftService.listPending(CurrentUser.familyId(), page, size));
    }

    @GetMapping("/count")
    public ApiResponse<TrashCountResponse> count() {
        return ApiResponse.ok(new TrashCountResponse(recurringDraftService.countPending(CurrentUser.familyId())));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<RecurringDraftResponse> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmDraftRequest request) {
        log.info("confirm - start, id={}", id);
        return ApiResponse.ok(recurringDraftService.confirm(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.email(), CurrentUser.displayName(), "OWNER".equals(CurrentUser.role()), request));
    }

    @PostMapping("/{id}/skip")
    public ApiResponse<RecurringDraftResponse> skip(@PathVariable Long id) {
        log.info("skip - start, id={}", id);
        return ApiResponse.ok(recurringDraftService.skip(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.displayName(), "OWNER".equals(CurrentUser.role())));
    }
}
