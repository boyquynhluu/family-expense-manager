package com.family.expensemanager.common.currency;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * @author boyquynhluu
 */
public class CurrencyUtil {

    /**
     * Format Bidecimal to String
     *
     * @param amount BigDecimal
     * @param currencyType String
     * @return formater String
     */
    public static String formatCurrency(BigDecimal amount) {
        NumberFormat formater =
                NumberFormat.getCurrencyInstance(
                        Locale.forLanguageTag("vi-VN")
                );

        formater.setMaximumFractionDigits(0);
        return formater.format(amount);
    }
}
