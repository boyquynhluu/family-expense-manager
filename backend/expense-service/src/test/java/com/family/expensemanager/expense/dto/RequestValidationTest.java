package com.family.expensemanager.expense.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * @author boyquynhluu
 */
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static TransactionRequest transaction(String amount, LocalDateTime occurredAt, String note) {
        return new TransactionRequest(1L, 2L, "EXPENSE", new BigDecimal(amount), occurredAt, note);
    }

    @Test
    void transaction_valid() {
        assertThat(validator.validate(transaction("150000.50", LocalDateTime.now(), "ok"))).isEmpty();
    }

    @Test
    void transaction_rejectsMoreThanTwoDecimals() {
        assertThat(validator.validate(transaction("1.005", LocalDateTime.now(), null))).hasSize(1);
    }

    @Test
    void transaction_rejectsAmountTooLargeForColumn() {
        assertThat(validator.validate(transaction("100000000000000000", LocalDateTime.now(), null))).hasSize(1);
    }

    @Test
    void transaction_rejectsNoteLongerThanColumn() {
        assertThat(validator.validate(transaction("10", LocalDateTime.now(), text(501)))).hasSize(1);
        assertThat(validator.validate(transaction("10", LocalDateTime.now(), text(500)))).isEmpty();
    }

    @Test
    void transaction_rejectsAbsurdDates() {
        assertThat(validator.validate(transaction("10", LocalDateTime.of(1999, 12, 31, 0, 0), null))).hasSize(1);
        assertThat(validator.validate(transaction("10", LocalDateTime.now().plusYears(2), null))).hasSize(1);
        assertThat(validator.validate(transaction("10", LocalDateTime.now().plusMonths(6), null))).isEmpty();
    }

    @Test
    void wallet_currencyMustBeRealIsoCode_caseInsensitive() {
        assertThat(validator.validate(new CreateWalletRequest("Ví", "VND", BigDecimal.ZERO))).isEmpty();
        assertThat(validator.validate(new CreateWalletRequest("Ví", "usd", BigDecimal.ZERO))).isEmpty();
        assertThat(validator.validate(new CreateWalletRequest("Ví", "abc", BigDecimal.ZERO))).hasSize(1);
        assertThat(validator.validate(new CreateWalletRequest("Ví", "XYZ", BigDecimal.ZERO))).hasSize(1);
    }

    @Test
    void wallet_rejectsTooLongNameAndTooManyDecimals() {
        assertThat(validator.validate(new CreateWalletRequest(text(256), "VND", BigDecimal.ZERO))).hasSize(1);
        assertThat(validator.validate(new CreateWalletRequest("Ví", "VND", new BigDecimal("1.001")))).hasSize(1);
    }

    @Test
    void budget_periodMonthMustBeARealMonth() {
        assertThat(validator.validate(new CreateBudgetRequest(null, "2026-12", BigDecimal.TEN))).isEmpty();
        assertThat(validator.validate(new CreateBudgetRequest(null, "2026-13", BigDecimal.TEN))).hasSize(1);
        assertThat(validator.validate(new CreateBudgetRequest(null, "2026-00", BigDecimal.TEN))).hasSize(1);
        assertThat(validator.validate(new CopyBudgetsRequest("2026-01", "2026-13"))).hasSize(1);
    }

    @Test
    void category_colorMustBeHex_iconAndNameBounded() {
        assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", "food", "#FF00aa"))).isEmpty();
        assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", null, null))).isEmpty();
        assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", null, "red"))).hasSize(1);
        assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", "i".repeat(51), null))).hasSize(1);
        assertThat(validator.validate(new CreateCategoryRequest(text(256), "EXPENSE", null, null))).hasSize(1);
    }

    @Test
    void categoryName_allowsLettersDigitsAndCommonSeparators_only() {
        for (String ok : new String[] {"Ăn uống & cà phê", "Điện/nước", "Học phí (con)", "Quà 8-3", "Xăng xe.", "Nhà's"}) {
            assertThat(validator.validate(new CreateCategoryRequest(ok, "EXPENSE", null, null))).as(ok).isEmpty();
        }
        for (String bad : new String[] {"<script>", "Ăn uống!", "Tiền $", "a@b", "#hashtag", "Ăn 🍔", "\"quote\"", "123"}) {
            assertThat(validator.validate(new CreateCategoryRequest(bad, "EXPENSE", null, null))).as(bad)
                    .extracting(v -> v.getMessage()).contains(CreateCategoryRequest.NAME_MESSAGE);
        }
    }

    @Test
    void walletName_allowsLettersDigitsAndCommonSeparators_only() {
        for (String ok : new String[] {"Ví Chồng", "Tiền mặt", "Ví 2", "Thẻ VCB (lương)", "Ví chung/nhà"}) {
            assertThat(validator.validate(new CreateWalletRequest(ok, "VND", BigDecimal.ZERO))).as(ok).isEmpty();
        }
        for (String bad : new String[] {"<script>", "Ví $", "Ví#1", "💰 Ví", "1", "Ví!"}) {
            assertThat(validator.validate(new CreateWalletRequest(bad, "VND", BigDecimal.ZERO))).as(bad)
                    .extracting(v -> v.getMessage()).contains(CreateWalletRequest.NAME_MESSAGE);
        }
    }

    @Test
    void categoryIcon_allowsShortCodesAndEmoji_butNoSymbols() {
        for (String ok : new String[] {"AI", "DX", "🍔", "❤️", "👍🏽", "👨‍👩‍👧", "1️⃣", "🇻🇳", "🛒 mua"}) {
            assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", ok, null))).as(ok).isEmpty();
        }
        for (String bad : new String[] {"<b>", "$$", "a;b", "%", "{x}"}) {
            assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", bad, null))).as(bad)
                    .extracting(v -> v.getMessage()).contains(CreateCategoryRequest.ICON_MESSAGE);
        }
    }

    @Test
    void categoryIcon_rejectsProfanity_butNotShortCodesThatLookLikeJunk() {
        assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", "fuck", null))).singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Nội dung có từ ngữ không phù hợp"));
        for (String icon : new String[] {"AI", "DX", "abc", "xx", "🍔"}) {
            assertThat(validator.validate(new CreateCategoryRequest("Ăn", "EXPENSE", icon, null))).as(icon).isEmpty();
        }
    }

    @Test
    void recurring_datesAreBounded() {
        var ok = new CreateRecurringTransactionRequest(1L, 2L, "EXPENSE", BigDecimal.TEN, null, "MONTHLY", 1, null, null,
                LocalDate.now().plusMonths(1), LocalDate.now().plusYears(10));
        assertThat(validator.validate(ok)).isEmpty();
        var bad = new CreateRecurringTransactionRequest(1L, 2L, "EXPENSE", BigDecimal.TEN, null, "MONTHLY", 1, null, null,
                LocalDate.of(1990, 1, 1), null);
        assertThat(validator.validate(bad)).hasSize(1);
    }

    @Test
    void transfer_amountScaleChecked() {
        var bad = new CreateWalletTransferRequest(1L, 2L, new BigDecimal("1.234"), LocalDateTime.now(), null);
        assertThat(validator.validate(bad)).hasSize(1);
    }

    /** Real-looking text of exactly {@code length} chars — a single repeated letter ("xxx...") is junk to @CleanText. */
    private static String text(int length) {
        return "Tiền chợ tuần này ".repeat(length / 18 + 1).substring(0, length);
    }
}
