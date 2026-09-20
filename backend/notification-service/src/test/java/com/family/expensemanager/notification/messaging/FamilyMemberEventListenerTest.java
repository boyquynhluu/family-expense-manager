package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.FamilyMemberEvent;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FamilyMemberEventListenerTest {

    @Mock
    private NotificationDao notificationDao;

    private FamilyMemberEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new FamilyMemberEventListener(
                notificationDao, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void onFamilyMemberEvent_recordsFamilyNotification_whenMemberJoined() {
        listener.onFamilyMemberEvent(event(FamilyMemberEvent.MEMBER_JOINED, "An"));

        Notification saved = captureInserted();
        assertThat(saved.getFamilyId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getType()).isEqualTo(FamilyMemberEvent.MEMBER_JOINED);
        assertThat(saved.getTitle()).isEqualTo("Thành viên mới");
        assertThat(saved.getMessage()).isEqualTo("An đã tham gia gia đình");
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getPayloadJson()).contains("MEMBER_JOINED");
    }

    @Test
    void onFamilyMemberEvent_recordsNotification_whenMemberLeft() {
        listener.onFamilyMemberEvent(event(FamilyMemberEvent.MEMBER_LEFT, "An"));

        Notification saved = captureInserted();
        assertThat(saved.getType()).isEqualTo(FamilyMemberEvent.MEMBER_LEFT);
        assertThat(saved.getMessage()).isEqualTo("An đã rời khỏi gia đình");
    }

    @Test
    void onFamilyMemberEvent_recordsNotification_whenMemberRemoved() {
        listener.onFamilyMemberEvent(event(FamilyMemberEvent.MEMBER_REMOVED, "An"));

        Notification saved = captureInserted();
        assertThat(saved.getType()).isEqualTo(FamilyMemberEvent.MEMBER_REMOVED);
        assertThat(saved.getMessage()).isEqualTo("An đã bị chủ hộ xoá khỏi gia đình");
    }

    @Test
    void onFamilyMemberEvent_usesFallbackName_whenDisplayNameMissing() {
        listener.onFamilyMemberEvent(event(FamilyMemberEvent.MEMBER_JOINED, null));

        assertThat(captureInserted().getMessage()).isEqualTo("Một thành viên đã tham gia gia đình");
    }

    @Test
    void onFamilyMemberEvent_ignoresUnknownEventType() {
        listener.onFamilyMemberEvent(event("SOMETHING_ELSE", "An"));

        verify(notificationDao, never()).insert(any());
    }

    private Notification captureInserted() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationDao).insert(captor.capture());
        return captor.getValue();
    }

    private static FamilyMemberEvent event(String type, String displayName) {
        return new FamilyMemberEvent(type, 1L, 5L, displayName, Instant.now());
    }
}
