package com.family.expensemanager.notification.dto;

import com.family.expensemanager.notification.domain.entity.Notification;

public record NotificationResponse(
        Long id,
        Long familyId,
        Long userId,
        String type,
        String title,
        String message,
        String payloadJson,
        Boolean isRead) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getFamilyId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getPayloadJson(),
                notification.getIsRead());
    }
}
