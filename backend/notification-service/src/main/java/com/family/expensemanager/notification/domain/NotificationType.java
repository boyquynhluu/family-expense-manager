package com.family.expensemanager.notification.domain;

/** Every type stored in NOTIFICATIONS.type; {@code emailSupported} marks the ones that also send an email. */
public enum NotificationType {
    BUDGET_EXCEEDED(true),
    BUDGET_WARNING(false),
    RECURRING_EXECUTED(false),
    RECURRING_FAILED(false),
    MEMBER_JOINED(false),
    MEMBER_LEFT(false),
    MEMBER_REMOVED(false),
    WALLET_TRANSFERRED(true);

    private final boolean emailSupported;

    NotificationType(boolean emailSupported) {
        this.emailSupported = emailSupported;
    }

    public boolean isEmailSupported() {
        return emailSupported;
    }

    public static NotificationType fromName(String name) {
        for (NotificationType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
