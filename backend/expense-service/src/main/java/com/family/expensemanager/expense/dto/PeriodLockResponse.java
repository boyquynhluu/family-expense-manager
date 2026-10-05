package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.PeriodLock;

import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record PeriodLockResponse(String periodMonth, Long lockedByUserId, String lockedByName, LocalDateTime lockedAt) {

    public static PeriodLockResponse from(PeriodLock lock) {
        return new PeriodLockResponse(
                lock.getPeriodMonth(), lock.getLockedByUserId(), lock.getLockedByName(), lock.getLockedAt());
    }
}
