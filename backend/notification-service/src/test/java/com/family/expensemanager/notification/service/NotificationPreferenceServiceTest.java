package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.notification.dao.NotificationPreferenceDao;
import com.family.expensemanager.notification.domain.NotificationType;
import com.family.expensemanager.notification.domain.entity.NotificationPreference;
import com.family.expensemanager.notification.dto.NotificationPreferenceRequest;
import com.family.expensemanager.notification.dto.NotificationPreferenceResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceDao preferenceDao;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferenceDao);
    }

    @Test
    void list_returnsEveryKnownTypeEnabled_whenUserSavedNothing() {
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of());

        List<NotificationPreferenceResponse> result = service.list(7L);

        String[] allTypes = Arrays.stream(NotificationType.values()).map(Enum::name).toArray(String[]::new);
        assertThat(result).extracting(NotificationPreferenceResponse::type).containsExactly(allTypes);
        assertThat(result).allMatch(p -> p.inAppEnabled() && p.emailEnabled());
        assertThat(result).filteredOn(NotificationPreferenceResponse::emailSupported)
                .extracting(NotificationPreferenceResponse::type).containsExactly("BUDGET_EXCEEDED");
    }

    @Test
    void list_reflectsSavedOptOuts() {
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of(
                preference(7L, "BUDGET_EXCEEDED", true, false), preference(7L, "MEMBER_JOINED", false, true)));

        List<NotificationPreferenceResponse> result = service.list(7L);

        NotificationPreferenceResponse budget = byType(result, "BUDGET_EXCEEDED");
        assertThat(budget.inAppEnabled()).isTrue();
        assertThat(budget.emailEnabled()).isFalse();
        assertThat(byType(result, "MEMBER_JOINED").inAppEnabled()).isFalse();
        assertThat(byType(result, "MEMBER_LEFT").inAppEnabled()).isTrue();
    }

    @Test
    void update_insertsNewRow_andUpdatesExistingRow() {
        NotificationPreference existing = preference(7L, "MEMBER_JOINED", true, true);
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of(existing));

        service.update(7L, List.of(
                new NotificationPreferenceRequest("MEMBER_JOINED", false, true),
                new NotificationPreferenceRequest("BUDGET_EXCEEDED", true, false)));

        assertThat(existing.getInAppEnabled()).isFalse();
        verify(preferenceDao).update(existing);
        ArgumentCaptor<NotificationPreference> inserted = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceDao).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(7L);
        assertThat(inserted.getValue().getType()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(inserted.getValue().getInAppEnabled()).isTrue();
        assertThat(inserted.getValue().getEmailEnabled()).isFalse();
    }

    @Test
    void update_keepsEmailEnabled_forTypesWithoutEmail() {
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of());

        service.update(7L, List.of(new NotificationPreferenceRequest("RECURRING_FAILED", false, false)));

        ArgumentCaptor<NotificationPreference> inserted = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceDao).insert(inserted.capture());
        assertThat(inserted.getValue().getInAppEnabled()).isFalse();
        assertThat(inserted.getValue().getEmailEnabled()).isTrue();
    }

    @Test
    void update_treatsMissingFlagsAsEnabled() {
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of());

        service.update(7L, List.of(new NotificationPreferenceRequest("BUDGET_EXCEEDED", null, null)));

        ArgumentCaptor<NotificationPreference> inserted = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceDao).insert(inserted.capture());
        assertThat(inserted.getValue().getInAppEnabled()).isTrue();
        assertThat(inserted.getValue().getEmailEnabled()).isTrue();
    }

    @Test
    void update_rejectsUnknownType_withoutWritingAnything() {
        List<NotificationPreferenceRequest> requests = List.of(
                new NotificationPreferenceRequest("MEMBER_JOINED", false, true),
                new NotificationPreferenceRequest("NOPE", false, true));

        assertThatThrownBy(() -> service.update(7L, requests)).isInstanceOf(BadRequestException.class);

        verify(preferenceDao, never()).insert(any());
        verify(preferenceDao, never()).update(any());
    }

    @Test
    void update_rejectsNullBody() {
        assertThatThrownBy(() -> service.update(7L, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void disabledInAppTypes_returnsOnlyTypesWithInAppOff() {
        when(preferenceDao.selectByUserId(7L)).thenReturn(List.of(
                preference(7L, "MEMBER_JOINED", false, true),
                preference(7L, "BUDGET_EXCEEDED", true, false)));

        assertThat(service.disabledInAppTypes(7L)).containsExactly("MEMBER_JOINED");
    }

    @Test
    void isEmailEnabled_defaultsToTrue_andHonoursOptOut() {
        when(preferenceDao.selectByUserIdAndType(7L, "BUDGET_EXCEEDED")).thenReturn(Optional.empty());
        when(preferenceDao.selectByUserIdAndType(8L, "BUDGET_EXCEEDED"))
                .thenReturn(Optional.of(preference(8L, "BUDGET_EXCEEDED", true, false)));

        assertThat(service.isEmailEnabled(7L, NotificationType.BUDGET_EXCEEDED)).isTrue();
        assertThat(service.isEmailEnabled(8L, NotificationType.BUDGET_EXCEEDED)).isFalse();
        assertThat(service.isEmailEnabled(null, NotificationType.BUDGET_EXCEEDED)).isTrue();
    }

    private static NotificationPreferenceResponse byType(List<NotificationPreferenceResponse> list, String type) {
        return list.stream().filter(p -> p.type().equals(type)).findFirst().orElseThrow();
    }

    private static NotificationPreference preference(Long userId, String type, boolean inApp, boolean email) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setType(type);
        preference.setInAppEnabled(inApp);
        preference.setEmailEnabled(email);
        return preference;
    }
}
