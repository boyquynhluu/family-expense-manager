package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.family.expensemanager.notification.dto.NotificationResponse;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "NotificationService")
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationDao notificationDao;
    private final NotificationPreferenceService preferenceService;

    /** Rows are per family; the caller's in-app opt-outs are filtered out here, at read time. */
    public PageResponse<NotificationResponse> listByFamilyPaged(Long familyId, Long userId, int page, int size) {
        try {
            log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            List<String> excludedTypes = preferenceService.disabledInAppTypes(userId);
            long totalElements = notificationDao.countByFamilyId(familyId, excludedTypes);
            List<NotificationResponse> content =
                    notificationDao.selectByFamilyIdPaged(familyId, excludedTypes, size, page * size).stream()
                            .map(NotificationResponse::from)
                            .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.listByFamilyPaged", e);
        }
    }

    public long countUnread(Long familyId, Long userId) {
        try {
            log.info("countUnread - start, familyId={}, userId={}", familyId, userId);
            return notificationDao.countUnreadByFamilyId(familyId, preferenceService.disabledInAppTypes(userId));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.countUnread", e);
        }
    }

    public void markAsRead(Long notificationId, Long familyId) {
        try {
            log.info("markAsRead - start, notificationId={}, familyId={}", notificationId, familyId);
            Notification notification = requireOwnedByFamily(notificationId, familyId);
            if (Boolean.TRUE.equals(notification.getIsRead())) {
                return;
            }
            notification.setIsRead(true);
            notificationDao.update(notification);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.markAsRead", e);
        }
    }

    public void markAllAsRead(Long familyId) {
        try {
            log.info("markAllAsRead - start, familyId={}", familyId);
            notificationDao.selectByFamilyId(familyId).stream()
                    .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                    .forEach(n -> {
                        n.setIsRead(true);
                        notificationDao.update(n);
                    });
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.markAllAsRead", e);
        }
    }

    public void delete(Long notificationId, Long familyId) {
        try {
            log.info("delete - start, notificationId={}, familyId={}", notificationId, familyId);
            notificationDao.delete(requireOwnedByFamily(notificationId, familyId));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.delete", e);
        }
    }

    public void deleteRead(Long familyId) {
        try {
            log.info("deleteRead - start, familyId={}", familyId);
            notificationDao.deleteReadByFamilyId(familyId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("NotificationService.deleteRead", e);
        }
    }

    private Notification requireOwnedByFamily(Long notificationId, Long familyId) {
        Notification notification = notificationDao.selectById(notificationId)
                .orElseThrow(() -> logged(log, new NotFoundException("Thông báo không tồn tại: " + notificationId)));
        if (!notification.getFamilyId().equals(familyId)) {
            throw logged(log, new NotFoundException("Thông báo không tồn tại: " + notificationId));
        }
        return notification;
    }
}
