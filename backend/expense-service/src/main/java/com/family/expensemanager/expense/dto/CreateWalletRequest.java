package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import com.family.expensemanager.common.validation.IsoCurrency;
import com.family.expensemanager.expense.domain.NameRules;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param ownerUserId the member who owns the wallet ("ví riêng"), or null for a wallet the whole family
 *                    shares ("ví chung"). Only the family OWNER can create/update wallets, and the client
 *                    only offers current members — expense-service has no member list of its own to
 *                    cross-check it against.
 *
 * @author boyquynhluu
 */
public record CreateWalletRequest(
        @NotBlank @Size(max = 255) @CleanText
        @Pattern(regexp = NameRules.NAME_REGEX, message = CreateWalletRequest.NAME_MESSAGE)
        String name,
        @NotBlank @IsoCurrency String currency,
        @NotNull @Digits(integer = 16, fraction = 2) BigDecimal initialBalance,
        @Positive Long ownerUserId) {

    public static final String NAME_MESSAGE =
            "Tên ví chỉ được chứa chữ, số, khoảng trắng và các ký tự " + NameRules.ALLOWED_SEPARATORS;

    /** A shared wallet (no owner). */
    public CreateWalletRequest(String name, String currency, BigDecimal initialBalance) {
        this(name, currency, initialBalance, null);
    }
}
