package com.family.expensemanager.expense.dto;

import com.family.expensemanager.common.validation.CleanText;
import jakarta.validation.constraints.Size;

/**
 * @author boyquynhluu
 */
public record RejectApprovalRequest(@Size(max = 255) @CleanText String reason) {
}
