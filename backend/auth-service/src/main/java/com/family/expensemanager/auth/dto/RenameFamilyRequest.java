package com.family.expensemanager.auth.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFamilyRequest(@NotBlank @Size(max = 100) @CleanText String name) {
}
