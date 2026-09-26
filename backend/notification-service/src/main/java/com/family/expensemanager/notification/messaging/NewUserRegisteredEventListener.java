package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.NewUserRegisteredEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code user-registered} events from auth-service and emails every system admin listed in
 * the event. Each admin gets their own message so addresses aren't exposed to each other, and one
 * failing address (or an unconfigured SMTP server) is logged instead of thrown, so Kafka doesn't
 * redeliver the event forever.
 */
@Component
@Slf4j(topic = "NewUserRegisteredEventListener")
public class NewUserRegisteredEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/new-user-registered-email.html";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final String template;

    public NewUserRegisteredEventListener(JavaMailSender mailSender,
                                           @Value("${app.frontend-url}") String frontendUrl,
                                           @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.template = loadTemplate();
    }

    @KafkaListener(topics = "${kafka.topic.user-registered}")
    public void onNewUserRegistered(NewUserRegisteredEvent event) {
        List<String> adminEmails = event.adminEmails();
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Bỏ qua email tài khoản mới userId={} vì không có admin nhận", event.userId());
            return;
        }

        String html = template
                .replace("{{displayName}}", escape(event.displayName()))
                .replace("{{email}}", escape(event.email()))
                .replace("{{familyName}}", escape(event.familyName()))
                .replace("{{sourceLabel}}", sourceLabel(event.source()))
                .replace("{{statusLabel}}", event.emailVerified() ? "Đã xác thực" : "Chờ xác thực email")
                .replace("{{registeredAt}}", event.occurredAt() == null ? "" : TIME_FORMAT.format(event.occurredAt()))
                .replace("{{adminLink}}", frontendUrl + "/admin");
        String subject = "Có người dùng mới đăng ký — " + event.displayName();

        for (String adminEmail : adminEmails) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
                helper.setFrom(fromAddress);
                helper.setTo(adminEmail);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
            } catch (MessagingException | MailException e) {
                log.error("Không gửi được email tài khoản mới userId={} cho admin {}", event.userId(), adminEmail, e);
            }
        }
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String sourceLabel(String source) {
        if (NewUserRegisteredEvent.SOURCE_GOOGLE.equals(source)) {
            return "Đăng nhập Google";
        }
        if (NewUserRegisteredEvent.SOURCE_FACEBOOK.equals(source)) {
            return "Đăng nhập Facebook";
        }
        if (NewUserRegisteredEvent.SOURCE_INVITE.equals(source)) {
            return "Lời mời vào gia đình";
        }
        return "Form đăng ký";
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
