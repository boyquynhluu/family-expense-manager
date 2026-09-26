package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageMagicBytesTest {

    @Test
    void detect_recognizesJpeg() {
        assertThat(ImageMagicBytes.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1}))
                .isEqualTo("image/jpeg");
    }

    @Test
    void detect_recognizesPng() {
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertThat(ImageMagicBytes.detect(header)).isEqualTo("image/png");
    }

    @Test
    void detect_recognizesWebp() {
        byte[] header = "RIFF????WEBP".getBytes();
        assertThat(ImageMagicBytes.detect(header)).isEqualTo("image/webp");
    }

    @Test
    void detect_returnsNull_forNonImageOrSpoofedContent() {
        assertThat(ImageMagicBytes.detect("<script>alert(1)</script>".getBytes())).isNull();
        assertThat(ImageMagicBytes.detect("%PDF-1.4".getBytes())).isNull();
        assertThat(ImageMagicBytes.detect(new byte[0])).isNull();
    }

    @Test
    void detect_returnsNull_forATruncatedHeaderTooShortToMatchAnySignature() {
        assertThat(ImageMagicBytes.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isNull();
    }
}
