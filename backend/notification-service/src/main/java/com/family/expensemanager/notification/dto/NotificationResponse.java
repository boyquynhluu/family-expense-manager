package com.family.expensemanager.notification.dto;

import com.family.expensemanager.notification.domain.entity.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * What the client sees of a notification. The stored payload JSON is deliberately left out: it is the raw event,
 * including the actor's email address, and every member of the family can list these notifications. Only its
 * {@code linkPath} is read out of it — the frontend page the notification is about, opened when it is clicked.
 * Older event types carry no linkPath, so they get their page from {@link #DEFAULT_LINKS}; null = nowhere to go.
 *
 * @author boyquynhluu
 */
public record NotificationResponse(
        Long id,
        Long familyId,
        Long userId,
        String type,
        String title,
        String message,
        Boolean isRead,
        String linkPath) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> DEFAULT_LINKS = Map.ofEntries(
            Map.entry("BUDGET_EXCEEDED", "/budgets"),
            Map.entry("BUDGET_WARNING", "/budgets"),
            Map.entry("RECURRING_EXECUTED", "/recurring-transactions"),
            Map.entry("RECURRING_FAILED", "/recurring-transactions"),
            Map.entry("MEMBER_JOINED", "/profile"),
            Map.entry("MEMBER_LEFT", "/profile"),
            Map.entry("MEMBER_REMOVED", "/profile"),
            Map.entry("WALLET_TRANSFERRED", "/wallets"),
            Map.entry("EXPENSE_DELETED", "/trash"),
            Map.entry("TRANSFER_REQUESTED", "/wallets"),
            Map.entry("TRANSFER_REQUEST_APPROVED", "/wallets"),
            Map.entry("TRANSFER_REQUEST_REJECTED", "/wallets"));

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getFamilyId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getIsRead(),
                linkOf(notification));
    }

    private static String linkOf(Notification notification) {
        String stored = storedLink(notification.getPayloadJson());
        if (stored != null || notification.getType() == null) {
            return stored;
        }
        return DEFAULT_LINKS.get(notification.getType());
    }

    /** The payload's linkPath — only an in-app path ("/loans"), never a full URL someone could have slipped in. */
    private static String storedLink(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode link = MAPPER.readTree(payloadJson).get("linkPath");
            String path = link == null || link.isNull() ? null : link.asText();
            return path != null && path.startsWith("/") && !path.startsWith("//") ? path : null;
        } catch (Exception e) {
            return null;
        }
    }
}
