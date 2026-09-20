package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;

/** {@code displayName} is null when no transaction in the range carries a creator name snapshot. */
public record MemberReportItem(Long userId, String displayName, BigDecimal income, BigDecimal expense) {
}
