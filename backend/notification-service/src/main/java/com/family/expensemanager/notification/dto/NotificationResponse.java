package com.family.expensemanager.notification.dto;

import com.family.expensemanager.notification.domain.entity.Notification;

/**
 * What the client sees of a notification. The stored payload JSON is deliberately left out: it is the raw event,
 * including the actor's email address, and every member of the family can list these notifications.
 */
public record NotificationResponse(
        Long id,
        Long familyId,
        Long userId,
        String type,
        String title,
        String message,
        Boolean isRead) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getFamilyId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getIsRead());
    }
}
