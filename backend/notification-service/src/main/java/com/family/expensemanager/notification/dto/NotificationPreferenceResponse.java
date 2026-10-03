package com.family.expensemanager.notification.dto;

/**
 * @author boyquynhluu
 */
public record NotificationPreferenceResponse(
        String type, boolean inAppEnabled, boolean emailEnabled, boolean emailSupported) {
}
