package com.family.expensemanager.notification.dto;

/**
 * @author boyquynhluu
 */
public record NotificationPreferenceRequest(String type, Boolean inAppEnabled, Boolean emailEnabled) {
}
