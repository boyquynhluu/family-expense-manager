package com.family.expensemanager.expense.dto;

/** {@code rowNumber} is 1-indexed counting the header row as row 1, matching what a user sees in Excel/a text editor. */
public record ImportRowError(int rowNumber, String message) {
}
