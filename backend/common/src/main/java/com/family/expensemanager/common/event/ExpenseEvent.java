package com.family.expensemanager.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload published by expense-service to the {@code expense-events} Kafka topic
 * (key = familyId) and consumed by notification-service. See README "Hợp đồng Kafka".
 * {@code periodMonth}/{@code limitAmount}/{@code totalSpent}/{@code categoryName}/
 * {@code userEmail}/{@code userDisplayName} are only populated for
 * {@link #BUDGET_EXCEEDED} and {@link #BUDGET_WARNING} (both with a null {@code categoryId}
 * and {@code categoryName} "Tổng chi tiêu" for the family-wide budget) — notification-service has no direct access to expense-
 * service's categories or auth-service's users, so expense-service (which has both,
 * the latter via the caller's JWT claims) fills them in at publish time rather than
 * notification-service making a synchronous cross-service call to look them up.
 * {@code occurredOn}/{@code note}/{@code categoryName} are populated for
 * {@link #RECURRING_EXECUTED} and {@link #RECURRING_FAILED} ({@code userId} is then the
 * rule's creator and {@code transactionId} is null for a failure).
 * {@code fromWalletName}/{@code toWalletName}/{@code userEmail}/{@code userDisplayName}
 * (the transfer's creator) are populated for {@link #WALLET_TRANSFERRED}; {@code amount}
 * and {@code occurredOn}/{@code note} carry the transfer's own amount/date/note,
 * {@code transactionId}/{@code categoryId} are null (a transfer is not a transaction).
 * Wallets belong to the family, not to one member (WALLETS has no owner column), so
 * there is no separate "recipient" to notify — the in-app row is shared by the whole
 * family (see notification-service's family-scoped listing) and the confirmation email
 * only goes to the creator, showing both legs of the transfer.
 * {@link #EXPENSE_DELETED}: {@code userId}/{@code userDisplayName} are who deleted it. For a single delete,
 * {@code transactionId}/{@code amount}/{@code occurredOn}/{@code note} describe that transaction and
 * {@code itemCount} is 1; a bulk delete publishes ONE event with {@code itemCount} = how many were deleted and
 * the per-transaction fields null, so deleting 50 rows doesn't flood the family's notifications.
 * {@code privateEntry}: the transaction is private (only its creator may see its details). Notifications are
 * listed family-wide, so the publisher already leaves the details OUT of such an event (amount/date/note/
 * category/transactionId null) — nothing private ever reaches notification-service, not even its stored
 * payload — and the consumer phrases the message generically. Null (older producers) = not private.
 * {@link #TRANSFER_REQUESTED} / {@link #TRANSFER_REQUEST_APPROVED} / {@link #TRANSFER_REQUEST_REJECTED}
 * ("yêu cầu chuyển tiền"): {@code userId}/{@code userDisplayName} are who acted (the requester, then the wallet
 * owner who decided), {@code targetUserId} is who must be told (the wallet owner, then the requester) — the
 * consumer looks their email up itself. {@code amount}/{@code note}/{@code fromWalletName}/{@code toWalletName}
 * describe the request. {@code targetUserId} is null for every other event type.
 *
 * @author boyquynhluu
 */
public record ExpenseEvent(
        String eventType,
        Long familyId,
        Long userId,
        Long transactionId,
        Long categoryId,
        BigDecimal amount,
        String periodMonth,
        BigDecimal limitAmount,
        BigDecimal totalSpent,
        String categoryName,
        String userEmail,
        String userDisplayName,
        Instant occurredAt,
        LocalDate occurredOn,
        String note,
        String fromWalletName,
        String toWalletName,
        Integer itemCount,
        Boolean privateEntry,
        Long targetUserId) {

    public static final String EXPENSE_CREATED = "EXPENSE_CREATED";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    public static final String BUDGET_WARNING = "BUDGET_WARNING";
    public static final String RECURRING_EXECUTED = "RECURRING_EXECUTED";
    public static final String RECURRING_FAILED = "RECURRING_FAILED";
    public static final String WALLET_TRANSFERRED = "WALLET_TRANSFERRED";
    public static final String EXPENSE_DELETED = "EXPENSE_DELETED";
    public static final String TRANSFER_REQUESTED = "TRANSFER_REQUESTED";
    public static final String TRANSFER_REQUEST_APPROVED = "TRANSFER_REQUEST_APPROVED";
    public static final String TRANSFER_REQUEST_REJECTED = "TRANSFER_REQUEST_REJECTED";

    // Explicit because the extra constructors below would otherwise leave Jackson's record-creator choice ambiguous.
    @JsonCreator
    public ExpenseEvent {
    }

    /** Pre-targetUserId shape — every publisher except the transfer-request ones. */
    public ExpenseEvent(String eventType, Long familyId, Long userId, Long transactionId, Long categoryId,
                        BigDecimal amount, String periodMonth, BigDecimal limitAmount, BigDecimal totalSpent,
                        String categoryName, String userEmail, String userDisplayName, Instant occurredAt,
                        LocalDate occurredOn, String note, String fromWalletName, String toWalletName,
                        Integer itemCount, Boolean privateEntry) {
        this(eventType, familyId, userId, transactionId, categoryId, amount, periodMonth, limitAmount, totalSpent,
                categoryName, userEmail, userDisplayName, occurredAt, occurredOn, note, fromWalletName, toWalletName,
                itemCount, privateEntry, null);
    }

    /** Pre-privateEntry shape — every publisher except a private transaction's own events. */
    public ExpenseEvent(String eventType, Long familyId, Long userId, Long transactionId, Long categoryId,
                        BigDecimal amount, String periodMonth, BigDecimal limitAmount, BigDecimal totalSpent,
                        String categoryName, String userEmail, String userDisplayName, Instant occurredAt,
                        LocalDate occurredOn, String note, String fromWalletName, String toWalletName,
                        Integer itemCount) {
        this(eventType, familyId, userId, transactionId, categoryId, amount, periodMonth, limitAmount, totalSpent,
                categoryName, userEmail, userDisplayName, occurredAt, occurredOn, note, fromWalletName, toWalletName,
                itemCount, null);
    }

    // Not "isPrivateEntry()": Jackson would read that as a second getter for the "privateEntry" property.
    public boolean hidesDetails() {
        return Boolean.TRUE.equals(privateEntry);
    }

    /** Pre-itemCount shape — still used by WALLET_TRANSFERRED publishers; itemCount only matters for EXPENSE_DELETED. */
    public ExpenseEvent(String eventType, Long familyId, Long userId, Long transactionId, Long categoryId,
                        BigDecimal amount, String periodMonth, BigDecimal limitAmount, BigDecimal totalSpent,
                        String categoryName, String userEmail, String userDisplayName, Instant occurredAt,
                        LocalDate occurredOn, String note, String fromWalletName, String toWalletName) {
        this(eventType, familyId, userId, transactionId, categoryId, amount, periodMonth, limitAmount, totalSpent,
                categoryName, userEmail, userDisplayName, occurredAt, occurredOn, note, fromWalletName, toWalletName,
                null, null);
    }

    /** Pre-occurredOn/note/wallet-name shape — still used by EXPENSE_CREATED/BUDGET_EXCEEDED/BUDGET_WARNING publishers. */
    public ExpenseEvent(String eventType, Long familyId, Long userId, Long transactionId, Long categoryId,
                        BigDecimal amount, String periodMonth, BigDecimal limitAmount, BigDecimal totalSpent,
                        String categoryName, String userEmail, String userDisplayName, Instant occurredAt) {
        this(eventType, familyId, userId, transactionId, categoryId, amount, periodMonth, limitAmount, totalSpent,
                categoryName, userEmail, userDisplayName, occurredAt, null, null, null, null, null, null);
    }

    /** Pre-wallet-name shape — still used by RECURRING_EXECUTED/RECURRING_FAILED publishers. */
    public ExpenseEvent(String eventType, Long familyId, Long userId, Long transactionId, Long categoryId,
                        BigDecimal amount, String periodMonth, BigDecimal limitAmount, BigDecimal totalSpent,
                        String categoryName, String userEmail, String userDisplayName, Instant occurredAt,
                        LocalDate occurredOn, String note) {
        this(eventType, familyId, userId, transactionId, categoryId, amount, periodMonth, limitAmount, totalSpent,
                categoryName, userEmail, userDisplayName, occurredAt, occurredOn, note, null, null, null, null);
    }
}
