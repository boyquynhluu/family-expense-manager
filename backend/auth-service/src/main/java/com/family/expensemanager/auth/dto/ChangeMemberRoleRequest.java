package com.family.expensemanager.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * README A5: MEMBER (thành viên), VIEWER (chỉ xem) or CHILD (trẻ em, chi tiêu có hạn mức). OWNER is handed over
 * through transfer-ownership instead, so a family always keeps exactly one.
 *
 * @author boyquynhluu
 */
public record ChangeMemberRoleRequest(
        @NotNull @Pattern(regexp = "MEMBER|VIEWER|CHILD", message = "Vai trò chỉ được là MEMBER, VIEWER hoặc CHILD")
        String role) {
}
