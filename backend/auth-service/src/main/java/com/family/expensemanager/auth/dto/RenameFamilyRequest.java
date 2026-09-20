package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFamilyRequest(@NotBlank @Size(max = 100) String name) {
}
