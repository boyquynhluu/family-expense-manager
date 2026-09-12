package com.family.expensemanager.notification.service;

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

    private final NotificationDao notificationDao;

    public List<NotificationResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return notificationDao.selectByFamilyId(familyId).stream().map(NotificationResponse::from).toList();
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
