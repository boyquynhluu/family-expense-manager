package com.family.expensemanager.expense.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * A receipt image read back from storage. equals/hashCode/toString are overridden because a record's generated
 * ones treat the byte[] by identity (two receipts with the same bytes would not be equal) and print only the
 * array's address; toString shows the size instead of dumping the image.
 *
 * @author boyquynhluu
 */
public record ReceiptFile(byte[] content, String contentType) {

    @Override
    public boolean equals(Object other) {
        return other instanceof ReceiptFile that
                && Arrays.equals(content, that.content)
                && Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(content) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
        return "ReceiptFile[contentType=" + contentType + ", size=" + (content == null ? 0 : content.length) + "]";
    }
}
