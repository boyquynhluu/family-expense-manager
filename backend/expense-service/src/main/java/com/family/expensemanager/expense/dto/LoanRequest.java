package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.ReasonableDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * README B3. {@code direction}: BORROWED (the family borrowed; the money comes INTO walletId) or LENT (the family
 * lent; it goes OUT of walletId). {@code memberUserId}: the family member the loan belongs to (null = the caller);
 * only the OWNER may name someone else. {@code counterpartyWalletId}: a loan INSIDE the family — the other side is
 * that family wallet (money moves between the two wallets); null = an outside person named {@code counterpartyName}. On an edit only the member, counterparty, due date and note may change.
 *
 * @author boyquynhluu
 */
public record LoanRequest(
        @NotNull @Pattern(regexp = "BORROWED|LENT") String direction,
        @Size(max = 100) @CleanText String counterpartyName,
        @Size(max = 100) @CleanText(junk = false) String counterpartyContact,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal principal,
        @NotNull Long walletId,
        @NotNull @ReasonableDate LocalDate startDate,
        @ReasonableDate(maxYearsAhead = 30) LocalDate dueDate,
        @Size(max = 255) @CleanText String note,
        Long memberUserId,
        Long counterpartyWalletId) {

    /** For the caller themself, with an outside counterparty. */
    public LoanRequest(String direction, String counterpartyName, String counterpartyContact, BigDecimal principal,
                       Long walletId, LocalDate startDate, LocalDate dueDate, String note) {
        this(direction, counterpartyName, counterpartyContact, principal, walletId, startDate, dueDate, note, null, null);
    }

    /** With an outside counterparty. */
    public LoanRequest(String direction, String counterpartyName, String counterpartyContact, BigDecimal principal,
                       Long walletId, LocalDate startDate, LocalDate dueDate, String note, Long memberUserId) {
        this(direction, counterpartyName, counterpartyContact, principal, walletId, startDate, dueDate, note,
                memberUserId, null);
    }
}
