package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.PeriodLockLogResponse;
import com.family.expensemanager.expense.dto.PeriodLockRequest;
import com.family.expensemanager.expense.dto.PeriodLockResponse;
import com.family.expensemanager.expense.service.PeriodLockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Chốt sổ theo tháng" (README B2). Every member may see which months are closed; only the OWNER may
 * close or reopen one (enforced in {@link PeriodLockService}).
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/period-locks")
@RequiredArgsConstructor
@Slf4j(topic = "PeriodLockController")
public class PeriodLockController {

    private final PeriodLockService periodLockService;

    @GetMapping
    public ApiResponse<List<PeriodLockResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(periodLockService.list(CurrentUser.familyId()));
    }

    @PostMapping
    public ApiResponse<PeriodLockResponse> lock(@Valid @RequestBody PeriodLockRequest request) {
        log.info("lock - start, periodMonth={}", request.periodMonth());
        return ApiResponse.ok(periodLockService.lock(
                CurrentUser.familyId(), request.periodMonth(), CurrentUser.userId(), CurrentUser.displayName()));
    }

    @DeleteMapping("/{periodMonth}")
    public ApiResponse<Void> unlock(@PathVariable String periodMonth) {
        log.info("unlock - start, periodMonth={}", periodMonth);
        periodLockService.unlock(CurrentUser.familyId(), periodMonth, CurrentUser.userId(), CurrentUser.displayName());
        return ApiResponse.ok();
    }

    @GetMapping("/history")
    public ApiResponse<PageResponse<PeriodLockLogResponse>> history(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("history - start, page={}, size={}", page, size);
        return ApiResponse.ok(periodLockService.history(CurrentUser.familyId(), page, size));
    }
}
