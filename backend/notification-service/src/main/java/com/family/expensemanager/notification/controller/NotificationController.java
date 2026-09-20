package com.family.expensemanager.notification.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.notification.dto.NotificationPreferenceRequest;
import com.family.expensemanager.notification.dto.NotificationPreferenceResponse;
import com.family.expensemanager.notification.dto.NotificationResponse;
import com.family.expensemanager.notification.dto.UnreadCountResponse;
import com.family.expensemanager.notification.service.NotificationPreferenceService;
import com.family.expensemanager.notification.service.NotificationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j(topic = "NotificationController")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("list - start, page={}, size={}", page, size);
        return ApiResponse.ok(
                notificationService.listByFamilyPaged(CurrentUser.familyId(), CurrentUser.userId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        log.info("unreadCount - start");
        return ApiResponse.ok(new UnreadCountResponse(
                notificationService.countUnread(CurrentUser.familyId(), CurrentUser.userId())));
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

    @DeleteMapping("/read")
    public ApiResponse<Void> deleteRead() {
        log.info("deleteRead - start");
        notificationService.deleteRead(CurrentUser.familyId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        notificationService.delete(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }

    @GetMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceResponse>> preferences() {
        log.info("preferences - start");
        return ApiResponse.ok(preferenceService.list(CurrentUser.userId()));
    }

    @PutMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceResponse>> updatePreferences(
            @RequestBody List<NotificationPreferenceRequest> requests) {
        log.info("updatePreferences - start");
        return ApiResponse.ok(preferenceService.update(CurrentUser.userId(), requests));
    }
}
