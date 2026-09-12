package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank String displayName,
        @NotBlank @Size(min = 8) String password) {
}
