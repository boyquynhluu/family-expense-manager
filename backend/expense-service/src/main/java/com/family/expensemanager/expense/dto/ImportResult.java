package com.family.expensemanager.expense.dto;

import java.util.List;

/**
 * @author boyquynhluu
 */
public record ImportResult(int totalRows, int importedCount, List<ImportRowError> errors) {
}
