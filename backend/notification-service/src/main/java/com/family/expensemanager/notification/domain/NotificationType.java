package com.family.expensemanager.notification.domain;

/**
 * Every type stored in NOTIFICATIONS.type; {@code emailSupported} marks the ones that also send an email.
 * {@code emailsActor} false: the member who caused it gets no email about their own action (the in-app row stays).
 *
 * @author boyquynhluu
 */
public enum NotificationType {
    BUDGET_EXCEEDED(true),
    BUDGET_WARNING(false),
    RECURRING_EXECUTED(false),
    RECURRING_FAILED(false),
    MEMBER_JOINED(false),
    MEMBER_LEFT(false),
    MEMBER_REMOVED(false),
    WALLET_TRANSFERRED(true),
    EXPENSE_DELETED(false),
    TRANSFER_REQUESTED(true),
    TRANSFER_REQUEST_APPROVED(false),
    TRANSFER_REQUEST_REJECTED(true),
    // Generic notices (ExpenseEvent.notice): title/message come ready-made from expense-service.
    EXPENSE_UPDATED(true, false),
    RECURRING_DRAFT_CREATED(true),
    BILL_DUE_SOON(true),
    APPROVAL_REQUESTED(true),
    APPROVAL_DECIDED(true),
    LOAN_DUE_SOON(true),
    SAVINGS_MILESTONE(true),
    MONTHLY_SUMMARY(true),
    LOAN_CREATED(true, false),
    LOAN_PAYMENT(true, false),
    LOAN_SETTLED(true, false),
    LOAN_REMOVED(false, false),
    WALLET_ADJUSTED(true, false);

    private final boolean emailSupported;
    private final boolean emailsActor;

    NotificationType(boolean emailSupported) {
        this(emailSupported, true);
    }

    NotificationType(boolean emailSupported, boolean emailsActor) {
        this.emailSupported = emailSupported;
        this.emailsActor = emailsActor;
    }

    public boolean isEmailSupported() {
        return emailSupported;
    }

    public boolean emailsActor() {
        return emailsActor;
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
