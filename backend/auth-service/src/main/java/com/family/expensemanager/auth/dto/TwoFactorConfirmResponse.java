package com.family.expensemanager.auth.dto;

import java.util.List;

/**
 * The plaintext recovery codes are only ever visible once, right here — only their hash is stored.
 *
 * @author boyquynhluu
 */
public record TwoFactorConfirmResponse(List<String> recoveryCodes) {
}
