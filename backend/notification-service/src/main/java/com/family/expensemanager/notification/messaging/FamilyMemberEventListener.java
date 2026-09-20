package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.FamilyMemberEvent;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code family-member-events} published by auth-service when someone joins a
 * family, leaves it, or is removed by the owner, and records an in-app {@link Notification}
 * for the whole family (no email).
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "FamilyMemberEventListener")
public class FamilyMemberEventListener {

    private static final String UNKNOWN_MEMBER = "Một thành viên";

    private final NotificationDao notificationDao;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topic.family-member-events}")
    public void onFamilyMemberEvent(FamilyMemberEvent event) {
        String name = event.memberDisplayName() != null && !event.memberDisplayName().isBlank()
                ? event.memberDisplayName() : UNKNOWN_MEMBER;
        String title;
        String message;
        switch (String.valueOf(event.eventType())) {
            case FamilyMemberEvent.MEMBER_JOINED -> {
                title = "Thành viên mới";
                message = name + " đã tham gia gia đình";
            }
            case FamilyMemberEvent.MEMBER_LEFT -> {
                title = "Thành viên rời gia đình";
                message = name + " đã rời khỏi gia đình";
            }
            case FamilyMemberEvent.MEMBER_REMOVED -> {
                title = "Thành viên bị xoá khỏi gia đình";
                message = name + " đã bị chủ hộ xoá khỏi gia đình";
            }
            default -> {
                log.warn("Bỏ qua family-member event không hỗ trợ: {}", event.eventType());
                return;
            }
        }

        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        notification.setUserId(event.memberUserId());
        notification.setType(event.eventType());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private String toJson(FamilyMemberEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Không serialize được FamilyMemberEvent: {}", event, e);
            throw new IllegalStateException("Không serialize được FamilyMemberEvent", e);
        }
    }
}
