package com.family.expensemanager.expense.dto;

/**
 * @author boyquynhluu
 */
public record BulkDeleteResult(int deleted, int skipped, int forbidden) {
}
