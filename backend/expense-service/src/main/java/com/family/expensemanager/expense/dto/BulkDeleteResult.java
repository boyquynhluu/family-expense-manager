package com.family.expensemanager.expense.dto;

/**
 * @param locked rows left alone because their month is closed (see PeriodLockService).
 *
 * @author boyquynhluu
 */
public record BulkDeleteResult(int deleted, int skipped, int forbidden, int locked) {
}
