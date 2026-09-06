package com.family.expensemanager.notification.service;

import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.dto.NotificationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationDao notificationDao;

    public NotificationService(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    public List<NotificationResponse> listByFamily(Long familyId) {
        return notificationDao.selectByFamilyId(familyId).stream().map(NotificationResponse::from).toList();
    }
}
