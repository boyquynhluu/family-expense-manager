package com.family.expensemanager.auth.dto;

import java.util.List;

/**
 * @author boyquynhluu
 */
public record PersonalDataExportResponse(
        UserProfileResponse profile,
        List<FamilyMembershipResponse> memberships,
        List<SessionResponse> sessions) {
}
