package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
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
 * The budget-exceeded email honours the creator's per-type email opt-out.
 */
@Component
@Slf4j(topic = "ExpenseEventListener")
public class ExpenseEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/budget-exceeded-email.html";
    private static final String OVERALL_BUDGET_LABEL = "Tổng chi tiêu";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationDao notificationDao;
    private final NotificationPreferenceService preferenceService;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final String template;

    public ExpenseEventListener(NotificationDao notificationDao,
                                 NotificationPreferenceService preferenceService,
                                 ObjectMapper objectMapper,
                                 JavaMailSender mailSender,
                                 @Value("${app.frontend-url}") String frontendUrl,
                                 @Value("${app.mail.from}") String fromAddress) {
        this.notificationDao = notificationDao;
        this.preferenceService = preferenceService;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.template = loadTemplate();
    }

    @KafkaListener(topics = "${kafka.topic.expense-events}")
    public void onExpenseEvent(ExpenseEvent event) throws MessagingException {
        if (ExpenseEvent.BUDGET_WARNING.equals(event.eventType())) {
            recordWarningNotification(event);
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
        if (!ExpenseEvent.BUDGET_EXCEEDED.equals(event.eventType())) {
            return;
        }

        recordNotification(event);

        if (!preferenceService.isEmailEnabled(event.userId(), NotificationType.BUDGET_EXCEEDED)) {
            log.info("Bỏ qua gửi email vượt ngân sách vì người dùng đã tắt, familyId={}, userId={}",
                    event.familyId(), event.userId());
        } else if (event.userEmail() != null) {
            sendBudgetExceededEmail(event);
        } else {
            log.warn("Bỏ qua gửi email vượt ngân sách vì thiếu userEmail, familyId={}, userId={}",
                    event.familyId(), event.userId());
        }
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
        notification.setUserId(event.userId());
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

    private String formatAmount(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private String toJson(ExpenseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Không serialize được ExpenseEvent: {}", event, e);
            throw new IllegalStateException("Không serialize được ExpenseEvent", e);
        }
    }

    private String loadTemplate() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(TEMPLATE_PATH).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Không đọc được template {}", TEMPLATE_PATH, e);
            throw new UncheckedIOException("Không đọc được template " + TEMPLATE_PATH, e);
        }
    }
}
