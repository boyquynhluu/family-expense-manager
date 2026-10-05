package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.IsoCurrency;
import com.family.expensemanager.expense.domain.NameRules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param walletType   C3: CASH (default), BANK, CREDIT_CARD or SAVINGS.
 * @param creditLimit  CREDIT_CARD only (required there): the card may go down to -creditLimit (README A1).
 * @param statementDay / paymentDueDay CREDIT_CARD only: day of month of the statement / the payment deadline
 *                     (a reminder goes out before the deadline, README C2).
 * @param interestRate / maturityDate SAVINGS only, informational.
 *
 * @author boyquynhluu
 */
public record CreateWalletRequest(
        @NotBlank @Size(max = 255) @CleanText
        @Pattern(regexp = NameRules.NAME_REGEX, message = CreateWalletRequest.NAME_MESSAGE)
        String name,
        @NotBlank @IsoCurrency String currency,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal initialBalance,
        @Positive Long ownerUserId,
        @Pattern(regexp = "CASH|BANK|CREDIT_CARD|SAVINGS") String walletType,
        @Positive @Digits(integer = 16, fraction = 2) BigDecimal creditLimit,
        @Min(1) @Max(31) Integer statementDay,
        @Min(1) @Max(31) Integer paymentDueDay,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal interestRate,
        LocalDate maturityDate) {

    public static final String NAME_MESSAGE =
            "Tên ví chỉ được chứa chữ, số, khoảng trắng và các ký tự " + NameRules.ALLOWED_SEPARATORS;

    /** A shared wallet (no owner). */
    public CreateWalletRequest(String name, String currency, BigDecimal initialBalance) {
        this(name, currency, initialBalance, null);
    }

    /** A plain CASH wallet. */
    public CreateWalletRequest(String name, String currency, BigDecimal initialBalance, Long ownerUserId) {
        this(name, currency, initialBalance, ownerUserId, null, null, null, null, null, null);
    }
}
