package com.family.expensemanager.expense.dto;

import java.time.LocalDate;

/** Same filter set the Transactions page applies client-side, now accepted by the export endpoint. */
public record TransactionReportFilter(
        Long walletId, Long categoryId, String type, LocalDate fromDate, LocalDate toDate) {
}
