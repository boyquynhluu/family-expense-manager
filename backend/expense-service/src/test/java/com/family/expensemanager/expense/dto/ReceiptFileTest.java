package com.family.expensemanager.expense.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReceiptFileTest {

    @Test
    void equality_comparesTheBytes_notTheArrayInstance() {
        ReceiptFile a = new ReceiptFile(new byte[] {1, 2, 3}, "image/png");
        ReceiptFile b = new ReceiptFile(new byte[] {1, 2, 3}, "image/png");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(new ReceiptFile(new byte[] {9}, "image/png"));
        assertThat(a).isNotEqualTo(new ReceiptFile(new byte[] {1, 2, 3}, "image/jpeg"));
        assertThat(a).isNotEqualTo("not a receipt");
    }

    @Test
    void toString_showsTheSize_notTheContent() {
        assertThat(new ReceiptFile(new byte[] {1, 2, 3}, "image/png"))
                .hasToString("ReceiptFile[contentType=image/png, size=3]");
        assertThat(new ReceiptFile(null, null)).hasToString("ReceiptFile[contentType=null, size=0]");
    }
}
