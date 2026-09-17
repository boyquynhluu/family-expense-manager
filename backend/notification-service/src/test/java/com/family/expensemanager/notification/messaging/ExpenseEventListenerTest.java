package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseEventListenerTest {

    @Mock
    private NotificationDao notificationDao;
    @Mock
    private JavaMailSender mailSender;

    private ExpenseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExpenseEventListener(
                notificationDao, new ObjectMapper().registerModule(new JavaTimeModule()), mailSender,
                "http://localhost:5173", "no-reply@fem.local");
    }

    @Test
    void onExpenseEvent_ignoresExpenseCreated() throws Exception {
        listener.onExpenseEvent(expenseCreatedEvent());

        verify(notificationDao, never()).insert(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onExpenseEvent_recordsNotificationAndSendsEmail_whenBudgetExceeded() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        listener.onExpenseEvent(budgetExceededEvent("user@b.com"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getFamilyId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(10L);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("user@b.com");
        assertThat(mimeMessage.getSubject()).contains("Ăn uống");
    }

    @Test
    void onExpenseEvent_skipsEmail_whenUserEmailMissing() throws Exception {
        listener.onExpenseEvent(budgetExceededEvent(null));

        verify(notificationDao).insert(any());
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private static ExpenseEvent expenseCreatedEvent() {
        return new ExpenseEvent(
                ExpenseEvent.EXPENSE_CREATED, 1L, 10L, 100L, 5L, BigDecimal.TEN,
                null, null, null, null, null, null, Instant.now());
    }

    private static ExpenseEvent budgetExceededEvent(String userEmail) {
        return new ExpenseEvent(
                ExpenseEvent.BUDGET_EXCEEDED, 1L, 10L, 100L, 5L, BigDecimal.valueOf(50),
                "2026-01", BigDecimal.valueOf(500), BigDecimal.valueOf(550), "Ăn uống",
                userEmail, "Chủ hộ", Instant.now());
    }
}
