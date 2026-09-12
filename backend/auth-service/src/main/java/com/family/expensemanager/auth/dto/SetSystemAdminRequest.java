package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotNull;

public record SetSystemAdminRequest(@NotNull Boolean isSystemAdmin) {
}
