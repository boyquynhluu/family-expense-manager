package com.family.expensemanager.auth.dto;

/**
 * Minimal contact card of a family member — only what notification-service needs to address an email.
 *
 * @author boyquynhluu
 */
public record FamilyMemberContactResponse(Long userId, String email, String displayName) {
}
