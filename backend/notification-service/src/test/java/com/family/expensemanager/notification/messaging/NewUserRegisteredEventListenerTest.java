package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.NewUserRegisteredEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewUserRegisteredEventListenerTest {

    @Mock
    private JavaMailSender mailSender;

    private NewUserRegisteredEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NewUserRegisteredEventListener(mailSender, "http://localhost:5173", "no-reply@fem.local");
    }

    @Test
    void onNewUserRegistered_sendsOneEmailPerAdmin_withEscapedUserInput() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        listener.onNewUserRegistered(event("<script>alert(1)</script>", List.of("a1@x.com", "a2@x.com")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        List<MimeMessage> sent = captor.getAllValues();
        assertThat(sent.get(0).getAllRecipients()).hasSize(1);
        assertThat(sent.get(0).getAllRecipients()[0].toString()).isEqualTo("a1@x.com");
        assertThat(sent.get(1).getAllRecipients()[0].toString()).isEqualTo("a2@x.com");
        assertThat(sent.get(0).getSubject()).contains("Có người dùng mới đăng ký");

        String body = String.valueOf(sent.get(0).getContent());
        assertThat(body).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(body).contains("http://localhost:5173/admin").contains("Chờ xác thực email").contains("Form đăng ký");
    }

    @Test
    void onNewUserRegistered_doesNothing_whenNoAdminEmails() {
        listener.onNewUserRegistered(event("An", List.of()));

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onNewUserRegistered_keepsGoingAndDoesNotThrow_whenSendingFails() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> listener.onNewUserRegistered(event("An", List.of("a1@x.com", "a2@x.com"))))
                .doesNotThrowAnyException();

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    private static NewUserRegisteredEvent event(String displayName, List<String> adminEmails) {
        return new NewUserRegisteredEvent(
                7L, "new@b.com", displayName, "Nhà An", NewUserRegisteredEvent.SOURCE_LOCAL, false,
                adminEmails, Instant.parse("2026-09-21T02:30:00Z"));
    }
}
