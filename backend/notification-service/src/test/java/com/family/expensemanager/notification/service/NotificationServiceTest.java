package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.exception.BadRequestException;
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
    @Mock
    private NotificationPreferenceService preferenceService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationDao, preferenceService);
    }

    @Test
    void listByFamilyPaged_returnsPageWithOffset() {
        when(preferenceService.disabledInAppTypes(7L)).thenReturn(List.of());
        when(notificationDao.countByFamilyId(1L, List.of())).thenReturn(12L);
        when(notificationDao.selectByFamilyIdPaged(1L, List.of(), 5, 10))
                .thenReturn(List.of(notification(11L, 1L, false), notification(12L, 1L, true)));

        var result = notificationService.listByFamilyPaged(1L, 7L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listByFamilyPaged_excludesTypesTheCallerDisabledInApp() {
        List<String> disabled = List.of("MEMBER_JOINED", "RECURRING_EXECUTED");
        when(preferenceService.disabledInAppTypes(7L)).thenReturn(disabled);
        when(notificationDao.countByFamilyId(1L, disabled)).thenReturn(1L);
        when(notificationDao.selectByFamilyIdPaged(1L, disabled, 5, 0))
                .thenReturn(List.of(notification(11L, 1L, false)));

        var result = notificationService.listByFamilyPaged(1L, 7L, 0, 5);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listByFamilyPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> notificationService.listByFamilyPaged(1L, 7L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> notificationService.listByFamilyPaged(1L, 7L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> notificationService.listByFamilyPaged(1L, 7L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void countUnread_delegatesToDao_excludingDisabledTypes() {
        List<String> disabled = List.of("BUDGET_WARNING");
        when(preferenceService.disabledInAppTypes(7L)).thenReturn(disabled);
        when(notificationDao.countUnreadByFamilyId(1L, disabled)).thenReturn(3L);

        assertThat(notificationService.countUnread(1L, 7L)).isEqualTo(3L);
    }

    @Test
    void delete_removesNotification_whenOwnedByFamily() {
        Notification notification = notification(1L, 1L, true);
        when(notificationDao.selectById(1L)).thenReturn(Optional.of(notification));

        notificationService.delete(1L, 1L);

        verify(notificationDao).delete(notification);
    }

    @Test
    void delete_throwsNotFound_whenBelongsToAnotherFamily() {
        when(notificationDao.selectById(1L)).thenReturn(Optional.of(notification(1L, 2L, true)));

        assertThatThrownBy(() -> notificationService.delete(1L, 1L)).isInstanceOf(NotFoundException.class);
        verify(notificationDao, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(notificationDao.selectById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRead_deletesReadNotificationsOfTheFamilyOnly() {
        notificationService.deleteRead(1L);

        verify(notificationDao).deleteReadByFamilyId(1L);
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
