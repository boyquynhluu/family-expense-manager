package com.family.expensemanager.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param approvalThreshold README A5: an expense above this (by anyone but the OWNER) waits for approval;
 *                          null = no approval step.
 *
 * @author boyquynhluu
 */
public record FamilySettingsRequest(@Positive @Digits(integer = 16, fraction = 2) BigDecimal approvalThreshold) {
}
