package com.family.expensemanager.expense.service;

/**
 * Detects an uploaded file's real image type from its first bytes ("magic numbers"), independent of
 * whatever {@code Content-Type} header or filename the client sent — both are fully attacker-controlled
 * (an HTTP client can label any bytes "image/jpeg"). Only covers the 3 formats
 * {@link TransactionService#ALLOWED_RECEIPT_CONTENT_TYPES} accepts for receipt photos.
 */
final class ImageMagicBytes {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageMagicBytes() {
    }

    /** Returns the sniffed content type, or {@code null} if {@code header} doesn't start with a recognized one. */
    static String detect(byte[] header) {
        if (startsWith(header, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(header, PNG_MAGIC)) {
            return "image/png";
        }
        if (isWebp(header)) {
            return "image/webp";
        }
        return null;
    }

    /** WebP is a RIFF container: bytes 0-3 "RIFF", bytes 8-11 "WEBP" (bytes 4-7 are the chunk size). */
    private static boolean isWebp(byte[] h) {
        return h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
