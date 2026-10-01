package com.family.expensemanager.expense.dto;

/**
 * Where a transaction sits in the (filtered, paginated) Transactions list — lets the page jump to it after
 * it was just added/edited, since a back-dated entry doesn't land on page 1.
 *
 * @param inList whether it matches the current filters at all (if not, {@code page} is meaningless)
 * @param page   0-based page holding it, for the requested page size
 */
public record TransactionLocation(boolean inList, int page) {
}
