package com.family.expensemanager.common.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FormulaInjectionGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"=cmd|'/c calc'!A0", "+1+1", "-1+1", "@SUM(1,1)", "=HYPERLINK(\"http://evil\",\"x\")"})
    void sanitize_prefixesValuesThatStartWithADangerousCharacter(String dangerous) {
        assertThat(FormulaInjectionGuard.sanitize(dangerous)).isEqualTo("'" + dangerous);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ăn uống", "Tiền nhà tháng 9", "a=b", "100-200", "note with @ inside, not first"})
    void sanitize_leavesOrdinaryTextUnchanged(String safe) {
        assertThat(FormulaInjectionGuard.sanitize(safe)).isEqualTo(safe);
    }

    @Test
    void sanitize_toleratesNullAndEmpty() {
        assertThat(FormulaInjectionGuard.sanitize(null)).isNull();
        assertThat(FormulaInjectionGuard.sanitize("")).isEmpty();
    }
}
