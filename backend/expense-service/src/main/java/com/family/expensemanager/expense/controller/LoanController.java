package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.LoanPaymentRequest;
import com.family.expensemanager.expense.dto.LoanRequest;
import com.family.expensemanager.expense.dto.LoanResponse;
import com.family.expensemanager.expense.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * README B3 "Vay / cho vay / nợ".
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/loans")
@RequiredArgsConstructor
@Slf4j(topic = "LoanController")
public class LoanController {

    private final LoanService loanService;

    @PostMapping
    public ApiResponse<LoanResponse> create(@Valid @RequestBody LoanRequest request) {
        log.info("create - start");
        return ApiResponse.ok(loanService.create(CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.displayName(),
                isOwner(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<LoanResponse>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long memberUserId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, status={}, page={}, size={}", status, page, size);
        return ApiResponse.ok(loanService.list(CurrentUser.familyId(), memberUserId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<LoanResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(loanService.get(CurrentUser.familyId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<LoanResponse> update(@PathVariable Long id, @Valid @RequestBody LoanRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(loanService.update(CurrentUser.familyId(), id, CurrentUser.userId(), isOwner(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        loanService.delete(CurrentUser.familyId(), id, CurrentUser.userId(), isOwner());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<LoanResponse> addPayment(@PathVariable Long id, @Valid @RequestBody LoanPaymentRequest request) {
        log.info("addPayment - start, id={}", id);
        return ApiResponse.ok(loanService.addPayment(CurrentUser.familyId(), id, CurrentUser.userId(),
                CurrentUser.displayName(), isOwner(), request));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ApiResponse<LoanResponse> deletePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        log.info("deletePayment - start, id={}, paymentId={}", id, paymentId);
        return ApiResponse.ok(loanService.deletePayment(CurrentUser.familyId(), id, paymentId, CurrentUser.userId(),
                isOwner()));
    }

    private static boolean isOwner() {
        return "OWNER".equals(CurrentUser.role());
    }
}
