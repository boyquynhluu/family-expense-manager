package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.notification.client.FamilyMemberDirectory;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.NotificationType;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.family.expensemanager.notification.service.NotificationPreferenceService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseEventListenerTest {

    @Mock
    private NotificationDao notificationDao;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private FamilyMemberDirectory memberDirectory;

    private ExpenseEventListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(preferenceService.isEmailEnabled(any(), eq(NotificationType.BUDGET_EXCEEDED))).thenReturn(true);
        lenient().when(preferenceService.isEmailEnabled(any(), eq(NotificationType.WALLET_TRANSFERRED))).thenReturn(true);
        listener = new ExpenseEventListener(
                notificationDao, preferenceService, new ObjectMapper().registerModule(new JavaTimeModule()),
                mailSender, memberDirectory, "http://localhost:5173", "no-reply@fem.local");
    }

    @Test
    void onExpenseEvent_recordsNotificationButSkipsEmail_whenUserDisabledEmailForBudgetExceeded() throws Exception {
        when(preferenceService.isEmailEnabled(10L, NotificationType.BUDGET_EXCEEDED)).thenReturn(false);

        listener.onExpenseEvent(budgetExceededEvent("user@b.com"));

        verify(notificationDao).insert(any());
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onExpenseEvent_recordsInAppNotification_whenRecurringExecuted() throws Exception {
        listener.onExpenseEvent(recurringEvent(ExpenseEvent.RECURRING_EXECUTED, 99L, "Tiền nhà", "Nhà ở"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(ExpenseEvent.RECURRING_EXECUTED);
        assertThat(saved.getFamilyId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getTitle()).isEqualTo("Giao dịch định kỳ đã được ghi");
        assertThat(saved.getMessage())
                .isEqualTo("Đã ghi giao dịch định kỳ \"Tiền nhà\" (Nhà ở) 5,000,000 vào ngày 01/02/2026");
        assertThat(saved.getPayloadJson()).contains("RECURRING_EXECUTED");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void onExpenseEvent_recordsInAppNotification_whenRecurringFailed_withoutNoteOrCategory() throws Exception {
        listener.onExpenseEvent(recurringEvent(ExpenseEvent.RECURRING_FAILED, null, null, null));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(ExpenseEvent.RECURRING_FAILED);
        assertThat(saved.getTitle()).isEqualTo("Giao dịch định kỳ không thực hiện được");
        assertThat(saved.getMessage()).isEqualTo(
                "Không ghi được giao dịch định kỳ 5,000,000 vào ngày 01/02/2026. "
                        + "Hệ thống sẽ thử lại vào lần chạy tiếp theo");
        verify(mailSender, never()).createMimeMessage();
    }

    private static ExpenseEvent recurringEvent(String type, Long transactionId, String note, String categoryName) {
        return new ExpenseEvent(
                type, 1L, 10L, transactionId, 5L, BigDecimal.valueOf(5000000), null, null, null, categoryName,
                "user@b.com", "Chủ hộ", Instant.now(), LocalDate.of(2026, 2, 1), note);
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

    @Test
    void onExpenseEvent_recordsNotificationOnly_whenBudgetWarning() throws Exception {
        listener.onExpenseEvent(budgetEvent(ExpenseEvent.BUDGET_WARNING, 5L, "Ăn uống", "user@b.com",
                BigDecimal.valueOf(850)));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(ExpenseEvent.BUDGET_WARNING);
        assertThat(saved.getTitle()).isEqualTo("Sắp vượt ngân sách tháng 2026-01");
        assertThat(saved.getMessage()).isEqualTo("Danh mục Ăn uống đã chi 850 / giới hạn 1,000 (đạt 85%)");
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onExpenseEvent_usesOverallLabel_whenBudgetWarningHasNoCategory() throws Exception {
        listener.onExpenseEvent(budgetEvent(ExpenseEvent.BUDGET_WARNING, null, "Tổng chi tiêu", "user@b.com",
                BigDecimal.valueOf(800)));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessage())
                .isEqualTo("Tổng chi tiêu đã chi 800 / giới hạn 1,000 (đạt 80%)");
    }

    @Test
    void onExpenseEvent_handlesOverallBudgetExceeded_withNullCategory() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        listener.onExpenseEvent(budgetEvent(ExpenseEvent.BUDGET_EXCEEDED, null, null, "user@b.com",
                BigDecimal.valueOf(1100)));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessage())
                .isEqualTo("Tổng chi tiêu đã chi 1,100 / giới hạn 1,000 trong tháng 2026-01");
        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).contains("Tổng chi tiêu");
    }

    private static ExpenseEvent budgetEvent(String type, Long categoryId, String categoryName, String userEmail,
                                             BigDecimal totalSpent) {
        return new ExpenseEvent(
                type, 1L, 10L, 100L, categoryId, BigDecimal.valueOf(50),
                "2026-01", BigDecimal.valueOf(1000), totalSpent, categoryName,
                userEmail, "Chủ hộ", Instant.now());
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

    @Test
    void onExpenseEvent_recordsSharedNotificationAndEmailsCreator_whenWalletTransferred() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        listener.onExpenseEvent(walletTransferEvent("user@b.com", "Tiền chợ"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(ExpenseEvent.WALLET_TRANSFERRED);
        assertThat(saved.getFamilyId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getTitle()).isEqualTo("Chuyển tiền giữa các ví");
        assertThat(saved.getMessage()).isEqualTo("Chủ hộ đã chuyển 200,000 từ Ví tiền mặt sang Ví ngân hàng");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("user@b.com");
        assertThat(mimeMessage.getSubject()).contains("200,000");
    }

    @Test
    void onExpenseEvent_skipsWalletTransferEmail_whenUserDisabledIt() throws Exception {
        when(preferenceService.isEmailEnabled(10L, NotificationType.WALLET_TRANSFERRED)).thenReturn(false);

        listener.onExpenseEvent(walletTransferEvent("user@b.com", null));

        verify(notificationDao).insert(any());
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onExpenseEvent_skipsWalletTransferEmail_whenUserEmailMissing() throws Exception {
        listener.onExpenseEvent(walletTransferEvent(null, null));

        verify(notificationDao).insert(any());
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onExpenseEvent_emailsOtherFamilyMembersButNotTheCreator_whenWalletTransferred() throws Exception {
        MimeMessage creatorMail = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage receiverMail = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(creatorMail, receiverMail);
        when(memberDirectory.listMembers(1L)).thenReturn(List.of(
                new FamilyMemberDirectory.Member(10L, "user@b.com", "Chủ hộ"),
                new FamilyMemberDirectory.Member(11L, "wife@b.com", "Hồng Huế")));

        listener.onExpenseEvent(walletTransferEvent("user@b.com", "Tiền chợ"));

        verify(mailSender).send(creatorMail);
        verify(mailSender).send(receiverMail);
        assertThat(creatorMail.getAllRecipients()[0].toString()).isEqualTo("user@b.com");
        assertThat(receiverMail.getAllRecipients()).hasSize(1);
        assertThat(receiverMail.getAllRecipients()[0].toString()).isEqualTo("wife@b.com");
        assertThat(receiverMail.getSubject()).contains("Chủ hộ").contains("200,000");
    }

    @Test
    void onExpenseEvent_skipsReceiverWhoOptedOut_andKeepsGoingWhenOneReceiverFails() throws Exception {
        MimeMessage okMail = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage failingMail = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage creatorMail = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(creatorMail, failingMail, okMail);
        when(memberDirectory.listMembers(1L)).thenReturn(List.of(
                new FamilyMemberDirectory.Member(11L, "optout@b.com", "A"),
                new FamilyMemberDirectory.Member(12L, "bad@b.com", "B"),
                new FamilyMemberDirectory.Member(13L, "ok@b.com", "C")));
        lenient().when(preferenceService.isEmailEnabled(11L, NotificationType.WALLET_TRANSFERRED)).thenReturn(false);
        org.mockito.Mockito.lenient().doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(failingMail);

        listener.onExpenseEvent(walletTransferEvent("user@b.com", null));

        verify(mailSender).send(creatorMail);
        verify(mailSender).send(failingMail);
        verify(mailSender).send(okMail);
        // creator + the failing receiver + the ok receiver — the opted-out one never even builds a message
        verify(mailSender, org.mockito.Mockito.times(3)).createMimeMessage();
    }

    @Test
    void onExpenseEvent_doesNotThrow_andStillEmailsReceivers_whenCreatorEmailFails() throws Exception {
        MimeMessage creatorMail = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage receiverMail = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(creatorMail, receiverMail);
        when(memberDirectory.listMembers(1L)).thenReturn(List.of(
                new FamilyMemberDirectory.Member(11L, "wife@b.com", "Hồng Huế")));
        org.mockito.Mockito.lenient().doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(creatorMail);

        org.assertj.core.api.Assertions.assertThatCode(
                () -> listener.onExpenseEvent(walletTransferEvent("user@b.com", null))).doesNotThrowAnyException();

        verify(notificationDao).insert(any());
        verify(mailSender).send(receiverMail);
    }

    @Test
    void onExpenseEvent_doesNotThrow_whenBudgetExceededEmailFails() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(mimeMessage);

        org.assertj.core.api.Assertions.assertThatCode(
                () -> listener.onExpenseEvent(budgetExceededEvent("user@b.com"))).doesNotThrowAnyException();

        verify(notificationDao).insert(any());
    }

    private static ExpenseEvent walletTransferEvent(String userEmail, String note) {
        return new ExpenseEvent(
                ExpenseEvent.WALLET_TRANSFERRED, 1L, 10L, null, null, BigDecimal.valueOf(200000),
                null, null, null, null, userEmail, "Chủ hộ", Instant.now(), LocalDate.of(2026, 2, 1), note,
                "Ví tiền mặt", "Ví ngân hàng");
    }
}
