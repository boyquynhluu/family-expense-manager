package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.EntityAuditLogResponse;
import com.family.expensemanager.expense.service.EntityAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * README A3: GET /api/expenses/audit-logs/{WALLET|BUDGET|TRANSFER}/{id} — the history of one wallet, budget or
 * transfer, visible to every member (like a transaction's history).
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/audit-logs")
@RequiredArgsConstructor
@Slf4j(topic = "AuditLogController")
public class AuditLogController {

    private final EntityAuditService entityAuditService;

    @GetMapping("/{entityType}/{entityId}")
    public ApiResponse<List<EntityAuditLogResponse>> history(@PathVariable String entityType,
                                                             @PathVariable Long entityId) {
        log.info("history - start, entityType={}, entityId={}", entityType, entityId);
        return ApiResponse.ok(entityAuditService.history(CurrentUser.familyId(), entityType, entityId));
    }
}
