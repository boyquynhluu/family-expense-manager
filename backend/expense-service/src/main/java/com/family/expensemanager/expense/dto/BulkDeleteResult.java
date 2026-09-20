package com.family.expensemanager.expense.dto;

public record BulkDeleteResult(int deleted, int skipped, int forbidden) {
}
