package com.family.expensemanager.expense.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The frontend sends/reads the flag as "isPrivate" — guard the JSON name of the boolean record component. */
class TransactionPrivacyJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void request_readsIsPrivate_andDefaultsToNullWhenAbsent() throws Exception {
        String base = "{\"walletId\":1,\"categoryId\":2,\"type\":\"EXPENSE\",\"amount\":10,"
                + "\"occurredAt\":\"2026-09-30T10:00:00\",\"note\":null";

        assertThat(mapper.readValue(base + ",\"isPrivate\":true}", TransactionRequest.class).isPrivate()).isTrue();
        assertThat(mapper.readValue(base + "}", TransactionRequest.class).isPrivate()).isNull();
    }

    @Test
    void response_writesIsPrivate() throws Exception {
        TransactionResponse response = new TransactionResponse(1L, 1L, 2L, 7L, 7L, "An", "EXPENSE", BigDecimal.TEN,
                LocalDateTime.of(2026, 9, 30, 10, 0), null, false, null, null, true);

        assertThat(mapper.writeValueAsString(response)).contains("\"isPrivate\":true");
    }
}
