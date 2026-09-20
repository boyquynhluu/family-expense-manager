package com.family.expensemanager.expense.dto;

import java.math.BigDecimal;
import java.util.List;

/** {@code bucketType} is "DAY" for ranges of at most 62 days, otherwise "MONTH"; buckets are zero-filled. */
public record RangeReportResponse(
        String from,
        String to,
        String bucketType,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        List<CategoryTotal> byCategory,
        List<BucketTotal> buckets) {

    public record CategoryTotal(Long categoryId, String type, BigDecimal total) {
    }

    public record BucketTotal(String bucket, BigDecimal income, BigDecimal expense) {
    }
}
