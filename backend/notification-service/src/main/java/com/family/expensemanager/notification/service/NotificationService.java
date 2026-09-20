package com.family.expensemanager.notification.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.family.expensemanager.notification.dto.NotificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "NotificationService")
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationDao notificationDao;

    public PageResponse<NotificationResponse> listByFamilyPaged(Long familyId, int page, int size) {
        log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = notificationDao.countByFamilyId(familyId);
        List<NotificationResponse> content = notificationDao.selectByFamilyIdPaged(familyId, size, page * size).stream()
                .map(NotificationResponse::from)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    public long countUnread(Long familyId) {
        log.info("countUnread - start, familyId={}", familyId);
        return notificationDao.countUnreadByFamilyId(familyId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long familyId) {
        log.info("markAsRead - start, notificationId={}, familyId={}", notificationId, familyId);
        Notification notification = requireOwnedByFamily(notificationId, familyId);
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }
        notification.setIsRead(true);
        notificationDao.update(notification);
    }

    @Transactional
    public void markAllAsRead(Long familyId) {
        log.info("markAllAsRead - start, familyId={}", familyId);
        notificationDao.selectByFamilyId(familyId).stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .forEach(n -> {
                    n.setIsRead(true);
                    notificationDao.update(n);
                });
    }

    private Notification requireOwnedByFamily(Long notificationId, Long familyId) {
        Notification notification = notificationDao.selectById(notificationId)
                .orElseThrow(() -> new NotFoundException("Thông báo không tồn tại: " + notificationId));
        if (!notification.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Thông báo không tồn tại: " + notificationId);
        }
        return notification;
    }
}
