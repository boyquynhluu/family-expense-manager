package com.family.expensemanager.notification.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.notification.dto.NotificationResponse;
import com.family.expensemanager.notification.dto.UnreadCountResponse;
import com.family.expensemanager.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j(topic = "NotificationController")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(notificationService.listByFamilyPaged(CurrentUser.familyId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        log.info("unreadCount - start");
        return ApiResponse.ok(new UnreadCountResponse(notificationService.countUnread(CurrentUser.familyId())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        log.info("markAsRead - start, id={}", id);
        notificationService.markAsRead(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        log.info("markAllAsRead - start");
        notificationService.markAllAsRead(CurrentUser.familyId());
        return ApiResponse.ok();
    }
}
