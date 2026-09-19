package com.family.expensemanager.auth.dto;

public record FamilyMembershipResponse(Long familyId, String familyName, String role, boolean isCurrent) {
}
