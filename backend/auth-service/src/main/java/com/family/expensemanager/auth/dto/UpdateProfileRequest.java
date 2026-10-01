package com.family.expensemanager.auth.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param relationship one of the fixed choices the Profile page offers (frontend RELATIONSHIP_OPTIONS), empty or
 *                     null for none — a free string here would let any text (profanity included) in via the API.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) @CleanText String displayName,
        @Size(max = 50)
        @Pattern(regexp = "|Bố|Mẹ|Ông|Bà|Anh|Chị|Em|Con|Cháu|Chồng|Vợ|Khác", message = "Quan hệ trong gia đình không hợp lệ")
        String relationship) {
}
