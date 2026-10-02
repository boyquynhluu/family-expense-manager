package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @author boyquynhluu
 */
public record TransferOwnershipRequest(@NotNull Long userId) {
}
