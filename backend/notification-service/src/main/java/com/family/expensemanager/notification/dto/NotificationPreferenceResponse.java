package com.family.expensemanager.notification.dto;

public record NotificationPreferenceResponse(
        String type, boolean inAppEnabled, boolean emailEnabled, boolean emailSupported) {
}
