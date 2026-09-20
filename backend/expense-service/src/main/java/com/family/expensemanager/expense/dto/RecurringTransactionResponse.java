package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.RecurringTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionResponse(
        Long id,
        Long walletId,
        Long categoryId,
        Long createdByUserId,
        String type,
        BigDecimal amount,
        String note,
        String frequency,
        Integer dayOfMonth,
        Integer dayOfWeek,
        Integer monthOfYear,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        Boolean active) {

    public static RecurringTransactionResponse from(RecurringTransaction r) {
        return new RecurringTransactionResponse(
                r.getId(), r.getWalletId(), r.getCategoryId(), r.getCreatedByUserId(), r.getType(), r.getAmount(), r.getNote(),
                r.getFrequency(), r.getDayOfMonth(), r.getDayOfWeek(), r.getMonthOfYear(),
                r.getStartDate(), r.getEndDate(), r.getNextRunDate(), r.getLastRunDate(),
                r.getActive());
    }
}
