package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.RecurringDraft;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author boyquynhluu
 */
public record RecurringDraftResponse(
        Long id, Long recurringId, Long walletId, Long categoryId, String type, BigDecimal suggestedAmount, String note,
        LocalDate dueDate, Long createdByUserId, String status, Long transactionId) {

    public static RecurringDraftResponse from(RecurringDraft d) {
        return new RecurringDraftResponse(d.getId(), d.getRecurringId(), d.getWalletId(), d.getCategoryId(), d.getType(),
                d.getSuggestedAmount(), d.getNote(), d.getDueDate(), d.getCreatedByUserId(), d.getStatus(),
                d.getTransactionId());
    }
}
