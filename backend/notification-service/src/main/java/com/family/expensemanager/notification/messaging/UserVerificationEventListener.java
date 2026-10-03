package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.UserVerificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code user-verification} events published by auth-service right after
 * registration, and emails the user an HTML verification link to the frontend's
 * {@code /verify?token=...} page, which calls the API then routes to {@code /login}.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "UserVerificationEventListener")
public class UserVerificationEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/verification-email.html";

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final long verificationTtlHours;
    private final String template;

    public UserVerificationEventListener(JavaMailSender mailSender,
                                          @Value("${app.frontend-url}") String frontendUrl,
                                          @Value("${app.mail.from}") String fromAddress,
                                          @Value("${app.mail.verification-ttl-hours}") long verificationTtlHours) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.verificationTtlHours = verificationTtlHours;
        this.template = loadTemplate();
    }

    // Non-blocking retry: a processing failure (DB hiccup, uncaught bug...) is retried 3 times with
    // backoff on dedicated retry topics instead of blocking this consumer; if it still fails, the record
    // lands on the auto-created "<topic>-dlt" topic instead of being silently dropped after the retries.
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000))
    @KafkaListener(topics = "${kafka.topic.user-verification}")
    public void onUserVerificationEvent(UserVerificationEvent event) throws MessagingException {
        String verifyLink = frontendUrl + "/verify?token=" + event.verificationToken();

        String html = template
                .replace("{{displayName}}", event.displayName())
                .replace("{{verifyLink}}", verifyLink)
                .replace("{{expiresInHours}}", String.valueOf(verificationTtlHours));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(event.email());
        helper.setSubject("Xác thực tài khoản Family Expense Manager");
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
