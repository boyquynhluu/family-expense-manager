package com.family.expensemanager.auth.dto;

/**
 * @author boyquynhluu
 */
public record FamilyMembershipResponse(Long familyId, String familyName, String role, boolean isCurrent) {
}
