package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
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
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code expense-events}. Per README "Hợp đồng Kafka", this service only
 * acts on {@link ExpenseEvent#BUDGET_EXCEEDED} at this stage — {@code EXPENSE_CREATED}
 * events are ignored. On a budget-exceeded event it both records an in-app
 * {@link Notification} and emails the transaction's creator, same pattern as
 * {@link PasswordResetEventListener} — see README tech-debt item "Cảnh báo vượt ngân
 * sách không gửi email", which this closes.
 */
@Component
@Slf4j(topic = "ExpenseEventListener")
public class ExpenseEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/budget-exceeded-email.html";

    private final NotificationDao notificationDao;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final String template;

    public ExpenseEventListener(NotificationDao notificationDao,
                                 ObjectMapper objectMapper,
                                 JavaMailSender mailSender,
                                 @Value("${app.frontend-url}") String frontendUrl,
                                 @Value("${app.mail.from}") String fromAddress) {
        this.notificationDao = notificationDao;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.template = loadTemplate();
    }

    @KafkaListener(topics = "${kafka.topic.expense-events}")
    public void onExpenseEvent(ExpenseEvent event) throws MessagingException {
        if (!ExpenseEvent.BUDGET_EXCEEDED.equals(event.eventType())) {
            return;
        }

        recordNotification(event);

        if (event.userEmail() != null) {
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
        notification.setMessage(String.format(
                "Danh mục #%d đã chi %s / giới hạn %s trong tháng %s",
                event.categoryId(), formatAmount(event.totalSpent()), formatAmount(event.limitAmount()),
                event.periodMonth()));
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private void sendBudgetExceededEmail(ExpenseEvent event) throws MessagingException {
        String html = template
                .replace("{{displayName}}", event.userDisplayName())
                .replace("{{categoryName}}", event.categoryName())
                .replace("{{periodMonth}}", event.periodMonth())
                .replace("{{totalSpent}}", formatAmount(event.totalSpent()))
                .replace("{{limitAmount}}", formatAmount(event.limitAmount()))
                .replace("{{budgetsLink}}", frontendUrl + "/budgets");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(event.userEmail());
        helper.setSubject("Cảnh báo vượt ngân sách — " + event.categoryName());
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
