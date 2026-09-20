package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotNull;

public record SwitchFamilyRequest(@NotNull Long familyId) {
}
