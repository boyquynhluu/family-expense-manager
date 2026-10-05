package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.PeriodLockLog;

import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record PeriodLockLogResponse(
        Long id, String periodMonth, String action, Long actorUserId, String actorName, LocalDateTime createdAt) {

    public static PeriodLockLogResponse from(PeriodLockLog log) {
        return new PeriodLockLogResponse(
                log.getId(), log.getPeriodMonth(), log.getAction(), log.getActorUserId(), log.getActorName(),
                log.getCreatedAt());
    }
}
