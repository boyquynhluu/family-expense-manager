package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.RecurringTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionResponse(
        Long id,
        Long walletId,
        Long categoryId,
        String type,
        BigDecimal amount,
        String note,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        Boolean active) {

    public static RecurringTransactionResponse from(RecurringTransaction r) {
        return new RecurringTransactionResponse(
                r.getId(), r.getWalletId(), r.getCategoryId(), r.getType(), r.getAmount(), r.getNote(),
                r.getDayOfMonth(), r.getStartDate(), r.getEndDate(), r.getNextRunDate(), r.getLastRunDate(),
                r.getActive());
    }
}
