package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationDao notificationDao;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationDao);
    }

    @Test
    void countUnread_delegatesToDao() {
        when(notificationDao.countUnreadByFamilyId(1L)).thenReturn(3L);

        assertThat(notificationService.countUnread(1L)).isEqualTo(3L);
    }

    @Test
    void markAsRead_setsIsReadTrue_whenUnread() {
        Notification notification = notification(1L, 1L, false);
        when(notificationDao.selectById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        assertThat(notification.getIsRead()).isTrue();
        verify(notificationDao).update(notification);
    }

    @Test
    void markAsRead_isNoOp_whenAlreadyRead() {
        Notification notification = notification(1L, 1L, true);
        when(notificationDao.selectById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        verify(notificationDao, never()).update(any());
    }

    @Test
    void markAsRead_throwsNotFound_whenBelongsToAnotherFamily() {
        Notification notification = notification(1L, 2L, false);
        when(notificationDao.selectById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllAsRead_updatesOnlyUnreadNotifications() {
        Notification unread1 = notification(1L, 1L, false);
        Notification unread2 = notification(2L, 1L, false);
        Notification alreadyRead = notification(3L, 1L, true);
        when(notificationDao.selectByFamilyId(1L)).thenReturn(List.of(unread1, unread2, alreadyRead));

        notificationService.markAllAsRead(1L);

        assertThat(unread1.getIsRead()).isTrue();
        assertThat(unread2.getIsRead()).isTrue();
        verify(notificationDao).update(unread1);
        verify(notificationDao).update(unread2);
        verify(notificationDao, never()).update(alreadyRead);
    }

    private static Notification notification(Long id, Long familyId, boolean isRead) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setFamilyId(familyId);
        notification.setIsRead(isRead);
        return notification;
    }
}
