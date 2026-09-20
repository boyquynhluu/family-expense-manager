package com.family.expensemanager.notification.dto;

public record NotificationPreferenceRequest(String type, Boolean inAppEnabled, Boolean emailEnabled) {
}
