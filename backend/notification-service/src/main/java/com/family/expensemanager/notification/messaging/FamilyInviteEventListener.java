package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.FamilyInviteEvent;
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
 * Consumes {@code family-invite} events published by auth-service when a family owner
 * invites a new member, and emails them a link to the frontend's
 * {@code /accept-invite?token=...} page — same pattern as {@link PasswordResetEventListener}.
 */
@Component
@Slf4j(topic = "FamilyInviteEventListener")
public class FamilyInviteEventListener {

    private static final String TEMPLATE_PATH = "mail-templates/family-invite-email.html";

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final long inviteTtlHours;
    private final String template;

    public FamilyInviteEventListener(JavaMailSender mailSender,
                                      @Value("${app.frontend-url}") String frontendUrl,
                                      @Value("${app.mail.from}") String fromAddress,
                                      @Value("${app.mail.invite-ttl-hours}") long inviteTtlHours) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.inviteTtlHours = inviteTtlHours;
        this.template = loadTemplate();
    }

    @KafkaListener(topics = "${kafka.topic.family-invite}")
    public void onFamilyInviteEvent(FamilyInviteEvent event) throws MessagingException {
        String acceptLink = frontendUrl + "/accept-invite?token=" + event.token();

        String html = template
                .replace("{{inviterDisplayName}}", event.inviterDisplayName())
                .replace("{{familyName}}", event.familyName())
                .replace("{{acceptLink}}", acceptLink)
                .replace("{{expiresInHours}}", String.valueOf(inviteTtlHours));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(event.email());
        helper.setSubject("Lời mời tham gia gia đình trên Family Expense Manager");
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
