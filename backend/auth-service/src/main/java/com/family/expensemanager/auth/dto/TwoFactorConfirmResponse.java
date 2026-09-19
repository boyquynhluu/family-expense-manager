package com.family.expensemanager.auth.dto;

import java.util.List;

/** The plaintext recovery codes are only ever visible once, right here — only their hash is stored. */
public record TwoFactorConfirmResponse(List<String> recoveryCodes) {
}
