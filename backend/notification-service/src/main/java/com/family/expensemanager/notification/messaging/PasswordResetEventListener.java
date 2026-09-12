package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.PasswordResetEvent;
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
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code password-reset} events published by auth-service when a user requests
 * a password reset, and emails them a link to the frontend's {@code /reset-password?token=...}
 * page — same pattern as {@link UserVerificationEventListener}.
 */
@Component
@Slf4j(topic = "PasswordResetEventListener")
public class PasswordResetEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/password-reset-email.html";

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final long resetPasswordTtlHours;
    private final String template;

    public PasswordResetEventListener(JavaMailSender mailSender,
                                       @Value("${app.frontend-url}") String frontendUrl,
                                       @Value("${app.mail.from}") String fromAddress,
                                       @Value("${app.mail.reset-password-ttl-hours}") long resetPasswordTtlHours) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.resetPasswordTtlHours = resetPasswordTtlHours;
        this.template = loadTemplate();
    }

    @KafkaListener(topics = "${kafka.topic.password-reset}")
    public void onPasswordResetEvent(PasswordResetEvent event) throws MessagingException {
        String resetLink = frontendUrl + "/reset-password?token=" + event.resetToken();

        String html = template
                .replace("{{displayName}}", event.displayName())
                .replace("{{resetLink}}", resetLink)
                .replace("{{expiresInHours}}", String.valueOf(resetPasswordTtlHours));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(event.email());
        helper.setSubject("Đặt lại mật khẩu Family Expense Manager");
        helper.setText(html, true);

        mailSender.send(message);
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
