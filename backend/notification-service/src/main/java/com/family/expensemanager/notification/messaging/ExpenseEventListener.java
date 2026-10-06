package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.notification.client.FamilyMemberDirectory;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.NotificationType;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.family.expensemanager.notification.service.NotificationPreferenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code expense-events}. Per README "Hợp đồng Kafka", this service only
 * acts on {@link ExpenseEvent#BUDGET_EXCEEDED} and {@link ExpenseEvent#BUDGET_WARNING} —
 * {@code EXPENSE_CREATED} events are ignored. On a budget-exceeded event it both records
 * an in-app {@link Notification} and emails the transaction's creator, same pattern as
 * {@link PasswordResetEventListener} — see README tech-debt item "Cảnh báo vượt ngân
 * sách không gửi email", which this closes. A budget-warning event (80% reached) only
 * records the in-app notification, no email. {@link ExpenseEvent#RECURRING_EXECUTED} and
 * {@link ExpenseEvent#RECURRING_FAILED} (from the recurring-transaction job) are in-app only.
 * The budget-exceeded email honours the creator's per-type email opt-out. On a
 * {@link ExpenseEvent#WALLET_TRANSFERRED} event, every family member gets the same shared
 * in-app row (wallets have no owner — see {@link ExpenseEvent}'s javadoc) and the transfer's
 * creator additionally gets a "đã trừ" confirmation email and every other family member (looked up
 * via {@link FamilyMemberDirectory}) gets a "đã cộng, ai chuyển" email, each gated by that user's
 * own per-type opt-out.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "ExpenseEventListener")
public class ExpenseEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/budget-exceeded-email.html";
    private static final String WALLET_TRANSFER_TEMPLATE_PATH = "mail-templates/wallet-transfer-email.html";
    private static final String WALLET_TRANSFER_RECEIVED_TEMPLATE_PATH = "mail-templates/wallet-transfer-received-email.html";
    private static final String TRANSFER_REQUEST_TEMPLATE_PATH = "mail-templates/transfer-request-email.html";
    private static final String TRANSFER_REQUEST_REJECTED_TEMPLATE_PATH = "mail-templates/transfer-request-rejected-email.html";
    private static final String NOTICE_TEMPLATE_PATH = "mail-templates/generic-notice-email.html";
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final String OVERALL_BUDGET_LABEL = "Tổng chi tiêu";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final NotificationDao notificationDao;
    private final NotificationPreferenceService preferenceService;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final String template;
    private final String walletTransferTemplate;
    private final String walletTransferReceivedTemplate;
    private final String transferRequestTemplate;
    private final String transferRequestRejectedTemplate;
    private final String noticeTemplate;
    private final FamilyMemberDirectory memberDirectory;

    public ExpenseEventListener(NotificationDao notificationDao,
                                 NotificationPreferenceService preferenceService,
                                 ObjectMapper objectMapper,
                                 JavaMailSender mailSender,
                                 FamilyMemberDirectory memberDirectory,
                                 @Value("${app.frontend-url}") String frontendUrl,
                                 @Value("${app.mail.from}") String fromAddress) {
        this.notificationDao = notificationDao;
        this.preferenceService = preferenceService;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.memberDirectory = memberDirectory;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.template = loadTemplate(TEMPLATE_PATH);
        this.walletTransferTemplate = loadTemplate(WALLET_TRANSFER_TEMPLATE_PATH);
        this.walletTransferReceivedTemplate = loadTemplate(WALLET_TRANSFER_RECEIVED_TEMPLATE_PATH);
        this.transferRequestTemplate = loadTemplate(TRANSFER_REQUEST_TEMPLATE_PATH);
        this.transferRequestRejectedTemplate = loadTemplate(TRANSFER_REQUEST_REJECTED_TEMPLATE_PATH);
        this.noticeTemplate = loadTemplate(NOTICE_TEMPLATE_PATH);
    }

    // Non-blocking retry: a processing failure (DB hiccup, uncaught bug...) is retried 3 times with
    // backoff on dedicated retry topics instead of blocking this consumer; if it still fails, the record
    // lands on the auto-created "<topic>-dlt" topic instead of being silently dropped after the retries.
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000))
    @KafkaListener(topics = "${kafka.topic.expense-events}")
    public void onExpenseEvent(ExpenseEvent event) throws MessagingException {
        if (event.carriesNotice()) {
            handleNotice(event);
            return;
        }
        if (ExpenseEvent.BUDGET_WARNING.equals(event.eventType())) {
            recordWarningNotification(event);
            return;
        }
        if (ExpenseEvent.EXPENSE_DELETED.equals(event.eventType())) {
            save(event, "Giao dịch đã bị xoá", deletedMessage(event));
            return;
        }
        if (ExpenseEvent.RECURRING_EXECUTED.equals(event.eventType())) {
            save(event, "Giao dịch định kỳ đã được ghi",
                    "Đã ghi " + recurringLabel(event) + dateSuffix(event));
            return;
        }
        if (ExpenseEvent.RECURRING_FAILED.equals(event.eventType())) {
            save(event, "Giao dịch định kỳ không thực hiện được",
                    "Không ghi được " + recurringLabel(event) + dateSuffix(event)
                            + ". Hệ thống sẽ thử lại vào lần chạy tiếp theo");
            return;
        }
        if (ExpenseEvent.TRANSFER_REQUESTED.equals(event.eventType())) {
            save(event, "Yêu cầu chuyển tiền", actor(event) + " yêu cầu chuyển " + formatAmount(event.amount())
                    + " từ " + event.fromWalletName() + " sang " + event.toWalletName() + " — đang chờ chủ ví đồng ý");
            sendTransferRequestEmail(event, transferRequestTemplate, NotificationType.TRANSFER_REQUESTED,
                    actor(event) + " yêu cầu bạn chuyển " + formatAmount(event.amount()));
            return;
        }
        if (ExpenseEvent.TRANSFER_REQUEST_APPROVED.equals(event.eventType())) {
            // In-app only: the transfer itself (WALLET_TRANSFERRED) already emails the family, requester included.
            save(event, "Yêu cầu chuyển tiền đã được duyệt", actor(event) + " đã đồng ý và chuyển "
                    + formatAmount(event.amount()) + " từ " + event.fromWalletName() + " sang " + event.toWalletName());
            return;
        }
        if (ExpenseEvent.TRANSFER_REQUEST_REJECTED.equals(event.eventType())) {
            save(event, "Yêu cầu chuyển tiền bị từ chối", actor(event) + " đã từ chối yêu cầu chuyển "
                    + formatAmount(event.amount()) + " từ " + nameOr(event.fromWalletName()) + " sang "
                    + nameOr(event.toWalletName()));
            sendTransferRequestEmail(event, transferRequestRejectedTemplate, NotificationType.TRANSFER_REQUEST_REJECTED,
                    "Yêu cầu chuyển " + formatAmount(event.amount()) + " đã bị từ chối");
            return;
        }
        if (ExpenseEvent.WALLET_TRANSFERRED.equals(event.eventType())) {
            recordTransferNotification(event);
            if (!preferenceService.isEmailEnabled(event.userId(), NotificationType.WALLET_TRANSFERRED)) {
                log.info("Bỏ qua gửi email chuyển tiền vì người dùng đã tắt, familyId={}, userId={}",
                        event.familyId(), event.userId());
            } else if (event.userEmail() != null) {
                try {
                    sendWalletTransferEmail(event);
                } catch (MessagingException | MailException e) {
                    // Not rethrown: Kafka would redeliver the event and insert the in-app notification again.
                    log.error("Không gửi được email chuyển tiền familyId={}, userId={}", event.familyId(), event.userId(), e);
                }
            } else {
                log.warn("Bỏ qua gửi email chuyển tiền vì thiếu userEmail, familyId={}, userId={}",
                        event.familyId(), event.userId());
            }
            sendWalletTransferReceivedEmails(event);
            return;
        }
        if (!ExpenseEvent.BUDGET_EXCEEDED.equals(event.eventType())) {
            return;
        }

        recordNotification(event);

        if (!preferenceService.isEmailEnabled(event.userId(), NotificationType.BUDGET_EXCEEDED)) {
            log.info("Bỏ qua gửi email vượt ngân sách vì người dùng đã tắt, familyId={}, userId={}",
                    event.familyId(), event.userId());
        } else if (event.userEmail() != null) {
            try {
                sendBudgetExceededEmail(event);
            } catch (MessagingException | MailException e) {
                // Not rethrown: Kafka would redeliver the event and insert the in-app notification again.
                log.error("Không gửi được email vượt ngân sách familyId={}, userId={}", event.familyId(), event.userId(), e);
            }
        } else {
            log.warn("Bỏ qua gửi email vượt ngân sách vì thiếu userEmail, familyId={}, userId={}",
                    event.familyId(), event.userId());
        }
    }

    /**
     * Generic notice (ExpenseEvent.notice): store the producer's own title/message as the family-wide in-app row,
     * then — for a type that supports email — email {@code targetUserId}, or every member with {@code targetRole},
     * or the whole family; each recipient's own opt-out is honoured and a failing address is only logged (a rethrow
     * would make Kafka redeliver and insert the in-app row twice).
     */
    private static final java.util.regex.Pattern USER_TOKEN = java.util.regex.Pattern.compile("\\{user:(\\d+)}");

    private void handleNotice(ExpenseEvent event) {
        NotificationType type = NotificationType.fromName(event.eventType());
        boolean emails = type != null && type.isEmailSupported();
        boolean namesUsers = hasUserToken(event.title()) || hasUserToken(event.message());
        List<FamilyMemberDirectory.Member> members = emails || namesUsers
                ? memberDirectory.listMembers(event.familyId()) : List.of();
        String title = resolveUserNames(event.title(), members);
        String message = resolveUserNames(event.message(), members);
        save(event, truncate(title, MAX_TITLE_LENGTH), truncate(message, MAX_MESSAGE_LENGTH));
        if (!emails) {
            return;
        }
        String link = frontendUrl + (event.linkPath() != null ? event.linkPath() : "/notifications");
        String messageHtml = escape(message).replace("\n", "<br>");
        for (FamilyMemberDirectory.Member member : members) {
            if (member.email() == null) {
                continue;
            }
            if (!type.emailsActor() && member.userId().equals(event.userId())) {
                continue;
            }
            if (event.recipientUserIds() != null) {
                if (!event.recipientUserIds().contains(member.userId())) {
                    continue;
                }
            } else if (event.targetUserId() != null && !event.targetUserId().equals(member.userId())) {
                continue;
            } else if (event.targetUserId() == null && event.targetRole() != null
                    && !event.targetRole().equals(member.role())) {
                continue;
            }
            if (!preferenceService.isEmailEnabled(member.userId(), type)) {
                log.info("Bỏ qua email {} vì người dùng đã tắt, familyId={}, userId={}", type, event.familyId(),
                        member.userId());
                continue;
            }
            String recipientName = member.displayName() != null ? member.displayName() : member.email();
            String html = noticeTemplate
                    .replace("{{recipientName}}", escape(recipientName))
                    .replace("{{title}}", escape(title))
                    .replace("{{message}}", messageHtml)
                    .replace("{{link}}", escape(link));
            try {
                sendHtml(member.email(), title, html);
            } catch (MessagingException | MailException e) {
                log.error("Không gửi được email {} familyId={}, userId={}", type, event.familyId(), member.userId(), e);
            }
        }
    }

    private static boolean hasUserToken(String text) {
        return text != null && text.contains("{user:");
    }

    /** {@code {user:<id>}} → that member's display name (see ExpenseEvent); unknown ids read "Một thành viên". */
    private static String resolveUserNames(String text, List<FamilyMemberDirectory.Member> members) {
        if (!hasUserToken(text)) {
            return text;
        }
        java.util.regex.Matcher matcher = USER_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Long userId = Long.valueOf(matcher.group(1));
            String name = members.stream()
                    .filter(m -> userId.equals(m.userId()))
                    .map(m -> m.displayName() != null ? m.displayName() : m.email())
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("Một thành viên");
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(name));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    private void recordNotification(ExpenseEvent event) {
        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        notification.setUserId(event.userId());
        notification.setType(event.eventType());
        notification.setTitle("Vượt ngân sách tháng " + event.periodMonth());
        String subject = event.categoryId() == null ? OVERALL_BUDGET_LABEL : "Danh mục #" + event.categoryId();
        notification.setMessage(String.format(
                "%s đã chi %s / giới hạn %s trong tháng %s",
                subject, formatAmount(event.totalSpent()), formatAmount(event.limitAmount()),
                event.periodMonth()));
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private void recordTransferNotification(ExpenseEvent event) {
        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        notification.setUserId(event.userId());
        notification.setType(event.eventType());
        notification.setTitle("Chuyển tiền giữa các ví");
        String actor = event.userDisplayName() != null ? event.userDisplayName() : "Một thành viên";
        notification.setMessage(String.format(
                "%s đã chuyển %s từ %s sang %s",
                actor, formatAmount(event.amount()), event.fromWalletName(), event.toWalletName()));
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private void recordWarningNotification(ExpenseEvent event) {
        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        notification.setUserId(event.userId());
        notification.setType(event.eventType());
        notification.setTitle("Sắp vượt ngân sách tháng " + event.periodMonth());
        notification.setMessage(String.format(
                "%s đã chi %s / giới hạn %s (đạt %d%%)",
                budgetLabel(event), formatAmount(event.totalSpent()), formatAmount(event.limitAmount()),
                percentUsed(event)));
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private void save(ExpenseEvent event, String title, String message) {
        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        // 0 = "Hệ thống" (a scheduled notice such as the monthly summary has no acting member).
        notification.setUserId(event.userId() != null ? event.userId() : 0L);
        notification.setType(event.eventType());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private String recurringLabel(ExpenseEvent event) {
        StringBuilder label = new StringBuilder("giao dịch định kỳ");
        if (event.note() != null && !event.note().isBlank()) {
            label.append(" \"").append(event.note().trim()).append('"');
        }
        if (event.categoryName() != null) {
            label.append(" (").append(event.categoryName()).append(')');
        }
        if (event.amount() != null) {
            label.append(' ').append(formatAmount(event.amount()));
        }
        return label.toString();
    }

    /** One transaction: what was removed; a bulk delete (itemCount > 1): just how many. Either way, where to undo it. */
    private String deletedMessage(ExpenseEvent event) {
        String actor = event.userDisplayName() != null ? event.userDisplayName() : "Một thành viên";
        StringBuilder message = new StringBuilder(actor);
        if (event.itemCount() != null && event.itemCount() > 1) {
            message.append(" đã xoá ").append(event.itemCount()).append(" giao dịch");
        } else if (event.hidesDetails()) {
            // Family-wide notification of a private transaction: the event carries no details anyway.
            message.append(" đã xoá 1 giao dịch riêng tư (***)");
        } else {
            message.append(" đã xoá giao dịch");
            if (event.amount() != null) {
                message.append(' ').append(formatAmount(event.amount()));
            }
            message.append(dateSuffix(event));
            if (event.note() != null && !event.note().isBlank()) {
                message.append(" \"").append(event.note().trim()).append('"');
            }
        }
        return message.append(". Có thể khôi phục trong Thùng rác.").toString();
    }

    private String dateSuffix(ExpenseEvent event) {
        return event.occurredOn() == null ? "" : " vào ngày " + event.occurredOn().format(DATE_FORMAT);
    }

    private String budgetLabel(ExpenseEvent event) {
        if (event.categoryId() == null) {
            return OVERALL_BUDGET_LABEL;
        }
        return event.categoryName() != null ? "Danh mục " + event.categoryName() : "Danh mục #" + event.categoryId();
    }

    private long percentUsed(ExpenseEvent event) {
        return event.totalSpent().multiply(BigDecimal.valueOf(100))
                .divide(event.limitAmount(), 0, RoundingMode.DOWN).longValue();
    }

    private void sendBudgetExceededEmail(ExpenseEvent event) throws MessagingException {
        String categoryName = event.categoryName() != null ? event.categoryName() : OVERALL_BUDGET_LABEL;
        String displayName = event.userDisplayName() != null ? event.userDisplayName() : event.userEmail();
        String html = template
                .replace("{{displayName}}", displayName)
                .replace("{{categoryName}}", categoryName)
                .replace("{{periodMonth}}", event.periodMonth())
                .replace("{{totalSpent}}", formatAmount(event.totalSpent()))
                .replace("{{limitAmount}}", formatAmount(event.limitAmount()))
                .replace("{{budgetsLink}}", frontendUrl + "/budgets");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(event.userEmail());
        helper.setSubject("Cảnh báo vượt ngân sách — " + categoryName);
        helper.setText(html, true);

        mailSender.send(message);
    }

    private void sendWalletTransferEmail(ExpenseEvent event) throws MessagingException {
        String displayName = event.userDisplayName() != null ? event.userDisplayName() : event.userEmail();
        String amount = formatAmount(event.amount());
        String html = walletTransferTemplate
                .replace("{{displayName}}", escape(displayName))
                .replace("{{occurredAt}}", event.occurredAt() == null ? "" : TIME_FORMAT.format(event.occurredAt()))
                .replace("{{amount}}", amount)
                .replace("{{fromWalletName}}", escape(event.fromWalletName()))
                .replace("{{toWalletName}}", escape(event.toWalletName()))
                .replace("{{noteBlock}}", noteBlock(event))
                .replace("{{walletsLink}}", frontendUrl + "/wallets");

        sendHtml(event.userEmail(), "Xác nhận chuyển tiền giữa các ví — " + amount, html);
    }

    /**
     * Wallets have no owner, so "the person who received the money" is every other member of the family.
     * Each one gets their own message (addresses aren't exposed to each other), honours their own opt-out,
     * and a failing address is logged instead of thrown so it can't block the rest or trigger a Kafka redelivery.
     */
    private void sendWalletTransferReceivedEmails(ExpenseEvent event) {
        String amount = formatAmount(event.amount());
        String senderName = event.userDisplayName() != null ? event.userDisplayName() : "Một thành viên";
        for (FamilyMemberDirectory.Member member : memberDirectory.listMembers(event.familyId())) {
            if (member.userId().equals(event.userId()) || member.email() == null) {
                continue;
            }
            if (!preferenceService.isEmailEnabled(member.userId(), NotificationType.WALLET_TRANSFERRED)) {
                log.info("Bỏ qua gửi email nhận tiền vì người dùng đã tắt, familyId={}, userId={}",
                        event.familyId(), member.userId());
                continue;
            }
            String recipientName = member.displayName() != null ? member.displayName() : member.email();
            String html = walletTransferReceivedTemplate
                    .replace("{{recipientName}}", escape(recipientName))
                    .replace("{{senderName}}", escape(senderName))
                    .replace("{{occurredAt}}", event.occurredAt() == null ? "" : TIME_FORMAT.format(event.occurredAt()))
                    .replace("{{amount}}", amount)
                    .replace("{{fromWalletName}}", escape(event.fromWalletName()))
                    .replace("{{toWalletName}}", escape(event.toWalletName()))
                    .replace("{{noteBlock}}", noteBlock(event))
                    .replace("{{walletsLink}}", frontendUrl + "/wallets");
            try {
                sendHtml(member.email(), senderName + " vừa chuyển " + amount + " vào ví gia đình", html);
            } catch (MessagingException | MailException e) {
                log.error("Không gửi được email nhận tiền familyId={}, userId={}", event.familyId(), member.userId(), e);
            }
        }
    }

    /**
     * One email, to the event's {@code targetUserId} only (the wallet owner for a new request, the requester for a
     * rejection), looked up through {@link FamilyMemberDirectory}. Failures are logged, never thrown: Kafka would
     * otherwise redeliver the event and insert its in-app notification again.
     */
    private void sendTransferRequestEmail(ExpenseEvent event, String template, NotificationType type, String subject) {
        if (event.targetUserId() == null) {
            log.warn("Bỏ qua email yêu cầu chuyển tiền vì thiếu targetUserId, familyId={}", event.familyId());
            return;
        }
        if (!preferenceService.isEmailEnabled(event.targetUserId(), type)) {
            log.info("Bỏ qua email {} vì người dùng đã tắt, familyId={}, userId={}", type, event.familyId(),
                    event.targetUserId());
            return;
        }
        FamilyMemberDirectory.Member recipient = memberDirectory.listMembers(event.familyId()).stream()
                .filter(m -> m.userId().equals(event.targetUserId()) && m.email() != null)
                .findFirst()
                .orElse(null);
        if (recipient == null) {
            log.warn("Không tìm thấy email người nhận yêu cầu chuyển tiền, familyId={}, userId={}",
                    event.familyId(), event.targetUserId());
            return;
        }
        String recipientName = recipient.displayName() != null ? recipient.displayName() : recipient.email();
        String html = template
                .replace("{{recipientName}}", escape(recipientName))
                // New request: the sender is the requester; rejection: the request was the recipient's own.
                .replace("{{senderName}}", escape(ExpenseEvent.TRANSFER_REQUESTED.equals(event.eventType())
                        ? actor(event) : recipientName))
                .replace("{{deciderName}}", escape(actor(event)))
                .replace("{{occurredAt}}", event.occurredAt() == null ? "" : TIME_FORMAT.format(event.occurredAt()))
                .replace("{{amount}}", formatAmount(event.amount()))
                .replace("{{fromWalletName}}", escape(nameOr(event.fromWalletName())))
                .replace("{{toWalletName}}", escape(nameOr(event.toWalletName())))
                .replace("{{noteBlock}}", noteBlock(event))
                .replace("{{walletsLink}}", frontendUrl + "/wallets");
        try {
            sendHtml(recipient.email(), subject, html);
        } catch (MessagingException | MailException e) {
            log.error("Không gửi được email {} familyId={}, userId={}", type, event.familyId(), event.targetUserId(), e);
        }
    }

    private static String actor(ExpenseEvent event) {
        return event.userDisplayName() != null ? event.userDisplayName() : "Một thành viên";
    }

    private static String nameOr(String walletName) {
        return walletName != null ? walletName : "ví đã xoá";
    }

    private String noteBlock(ExpenseEvent event) {
        if (event.note() == null || event.note().isBlank()) {
            return "";
        }
        return ""
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin-bottom:16px;\"><tr><td style=\"background-color:#f4f5f7;border-radius:6px;"
                + "padding:12px 16px;\"><p style=\"font-size:12px;color:#7b8794;margin:0 0 4px;"
                + "text-transform:uppercase;letter-spacing:0.04em;\">Ghi chú</p>"
                + "<p style=\"font-size:14px;color:#1f2933;margin:0;\">" + escape(event.note()) + "</p>"
                + "</td></tr></table>";
    }

    private void sendHtml(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private String formatAmount(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private String toJson(ExpenseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Không serialize được ExpenseEvent: {}", event, e);
            throw new IllegalStateException("Không serialize được ExpenseEvent", e);
        }
    }

    private String loadTemplate(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Không đọc được template {}", path, e);
            throw new UncheckedIOException("Không đọc được template " + path, e);
        }
    }
}
