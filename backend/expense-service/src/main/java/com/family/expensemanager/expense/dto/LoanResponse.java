package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Loan;
import com.family.expensemanager.expense.domain.entity.LoanPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param remaining principal minus every repayment.
 *
 * @author boyquynhluu
 */
public record LoanResponse(
        Long id, Long memberUserId, String direction, String counterpartyName, String counterpartyContact, Long counterpartyWalletId, BigDecimal principal,
        BigDecimal paid, BigDecimal remaining, Long walletId, LocalDate startDate, LocalDate dueDate, String note,
        String status, Long createdByUserId, String createdByName, List<Payment> payments) {

    public record Payment(Long id, Long walletId, BigDecimal amount, LocalDateTime paidAt, String note,
                          Long createdByUserId, String createdByName) {

        static Payment from(LoanPayment p) {
            return new Payment(p.getId(), p.getWalletId(), p.getAmount(), p.getPaidAt(), p.getNote(),
                    p.getCreatedByUserId(), p.getCreatedByName());
        }
    }

    public static LoanResponse from(Loan l, BigDecimal paid, List<LoanPayment> payments) {
        return new LoanResponse(l.getId(), l.getMemberUserId(), l.getDirection(), l.getCounterpartyName(), l.getCounterpartyContact(), l.getCounterpartyWalletId(),
                l.getPrincipal(), paid, l.getPrincipal().subtract(paid), l.getWalletId(), l.getStartDate(),
                l.getDueDate(), l.getNote(), l.getStatus(), l.getCreatedByUserId(), l.getCreatedByName(),
                payments == null ? null : payments.stream().map(Payment::from).toList());
    }
}
