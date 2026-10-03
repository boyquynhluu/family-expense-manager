package com.family.expensemanager.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author boyquynhluu
 */
class TextQualityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "lồn", "Đồ LỒN", "l.ồ.n", "l ồ n", "đ-ụ", "địt mẹ", "Đéo trả", "óc chó", "vcl", "vcllll", "VKL",
            "đcm", "dcm thằng kia", "djt", "clgt", "fuck you", "fuuuck", "F.U.C.K", "bullshit", "What the shit"})
    void flagsProfanity(String text) {
        assertThat(TextQuality.check(text)).contains(TextQuality.Problem.PROFANITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test", "TEST", "testtt", "Test123", "xxx", "XXXXX", "asdf", "qwerty", "abc", "123", "123456", "aaaa",
            "1111", "...", "!!!", "  ---  ", "hihi", "lorem ipsum"})
    void flagsJunk(String text) {
        assertThat(TextQuality.check(text)).contains(TextQuality.Problem.JUNK);
    }

    // The words whose unaccented form IS a blocked word, sentences merely CONTAINING a junk word, nicknames, units...
    @ParameterizedTest
    @ValueSource(strings = {
            "Mua 2 lon bia", "Các khoản chi tháng 9", "Du lịch Đà Lạt", "Đeo kính", "Dái tai", "Điểm thưởng",
            "Phí test COVID", "Mua 123 cái bánh", "Lili", "Bibi", "Nana", "Mama", "2 dm vải", "Ví chính",
            "Ăn uống", "Tiền điện", "Quỹ chung", "Đi chợ", "Xăng xe", "Abc Bank", "Thu Hà", "🎂", "Lương tháng 9",
            "Cho bé đi học", "Thằng Tí đóng học phí", "Tết", "Mua đồ Tết"})
    void acceptsNormalText(String text) {
        assertThat(TextQuality.check(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void leavesBlankToNotBlank(String text) {
        assertThat(TextQuality.check(text)).isEmpty();
        assertThat(TextQuality.check(null)).isEmpty();
    }
}
