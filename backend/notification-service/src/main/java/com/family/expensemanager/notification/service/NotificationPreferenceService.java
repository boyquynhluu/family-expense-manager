package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.notification.dao.NotificationPreferenceDao;
import com.family.expensemanager.notification.domain.NotificationType;
import com.family.expensemanager.notification.domain.entity.NotificationPreference;
import com.family.expensemanager.notification.dto.NotificationPreferenceRequest;
import com.family.expensemanager.notification.dto.NotificationPreferenceResponse;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * Per-user opt-outs, stored only when a user changed something — a missing row means
 * "enabled". Notifications are stored per family (shared list and read state), so the
 * in-app opt-out is applied when a user reads the list, not when the row is inserted.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "NotificationPreferenceService")
public class NotificationPreferenceService {

    private final NotificationPreferenceDao preferenceDao;

    public List<NotificationPreferenceResponse> list(Long userId) {
        try {
            log.info("list - start, userId={}", userId);
            Map<String, NotificationPreference> saved = savedByType(userId);
            return Arrays.stream(NotificationType.values())
                    .map(type -> toResponse(type, saved.get(type.name())))
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationPreferenceService.list", e);
        }
    }

    public List<NotificationPreferenceResponse> update(Long userId, List<NotificationPreferenceRequest> requests) {
        try {
            log.info("update - start, userId={}", userId);
            if (requests == null) {
                throw logged(log, new BadRequestException("Danh sách tuỳ chọn không được để trống"));
            }
            Map<NotificationType, NotificationPreferenceRequest> byType = new LinkedHashMap<>();
            for (NotificationPreferenceRequest request : requests) {
                NotificationType type = request == null ? null : NotificationType.fromName(request.type());
                if (type == null) {
                    throw logged(log, new BadRequestException("Loại thông báo không hợp lệ: " + (request == null ? null : request.type())));
                }
                byType.put(type, request);
            }

            Map<String, NotificationPreference> saved = savedByType(userId);
            byType.forEach((type, request) -> {
                boolean inApp = !Boolean.FALSE.equals(request.inAppEnabled());
                boolean email = !type.isEmailSupported() || !Boolean.FALSE.equals(request.emailEnabled());
                NotificationPreference existing = saved.get(type.name());
                if (existing != null) {
                    existing.setInAppEnabled(inApp);
                    existing.setEmailEnabled(email);
                    preferenceDao.update(existing);
                    return;
                }
                NotificationPreference created = new NotificationPreference();
                created.setUserId(userId);
                created.setType(type.name());
                created.setInAppEnabled(inApp);
                created.setEmailEnabled(email);
                preferenceDao.insert(created);
                saved.put(type.name(), created);
            });
            return Arrays.stream(NotificationType.values())
                    .map(type -> toResponse(type, saved.get(type.name())))
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationPreferenceService.update", e);
        }
    }

    public List<String> disabledInAppTypes(Long userId) {
        try {
            return preferenceDao.selectByUserId(userId).stream()
                    .filter(p -> Boolean.FALSE.equals(p.getInAppEnabled()))
                    .map(NotificationPreference::getType)
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationPreferenceService.disabledInAppTypes", e);
        }
    }

    public boolean isEmailEnabled(Long userId, NotificationType type) {
        try {
            if (userId == null) {
                return true;
            }
            return preferenceDao.selectByUserIdAndType(userId, type.name())
                    .map(p -> !Boolean.FALSE.equals(p.getEmailEnabled()))
                    .orElse(true);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationPreferenceService.isEmailEnabled", e);
        }
    }

    private Map<String, NotificationPreference> savedByType(Long userId) {
        Map<String, NotificationPreference> saved = new HashMap<>();
        preferenceDao.selectByUserId(userId).forEach(p -> saved.put(p.getType(), p));
        return saved;
    }

    private static NotificationPreferenceResponse toResponse(NotificationType type, NotificationPreference saved) {
        boolean inApp = saved == null || !Boolean.FALSE.equals(saved.getInAppEnabled());
        boolean email = saved == null || !Boolean.FALSE.equals(saved.getEmailEnabled());
        return new NotificationPreferenceResponse(type.name(), inApp, email, type.isEmailSupported());
    }
}
