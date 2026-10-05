package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @author boyquynhluu
 */
public record PeriodLockRequest(
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Tháng không hợp lệ (định dạng yyyy-MM)")
        String periodMonth) {
}
