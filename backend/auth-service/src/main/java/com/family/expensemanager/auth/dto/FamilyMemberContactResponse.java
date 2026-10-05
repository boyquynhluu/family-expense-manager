package com.family.expensemanager.auth.dto;

/**
 * Minimal contact card of a family member — only what notification-service needs to address an email.
 * {@code role} is the member's role in THAT family (e.g. to email only the OWNER about an approval request).
 *
 * @author boyquynhluu
 */
public record FamilyMemberContactResponse(Long userId, String email, String displayName, String role) {
}
