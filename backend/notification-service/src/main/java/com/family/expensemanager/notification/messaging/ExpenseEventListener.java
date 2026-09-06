package com.family.expensemanager.notification.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code expense-events}. Per README "Hợp đồng Kafka", this service only
 * acts on {@link ExpenseEvent#BUDGET_EXCEEDED} at this stage — {@code EXPENSE_CREATED}
 * events are ignored.
 */
@Component
public class ExpenseEventListener {

    private final NotificationDao notificationDao;
    private final ObjectMapper objectMapper;

    public ExpenseEventListener(NotificationDao notificationDao, ObjectMapper objectMapper) {
        this.notificationDao = notificationDao;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topic.expense-events}")
    public void onExpenseEvent(ExpenseEvent event) {
        if (!ExpenseEvent.BUDGET_EXCEEDED.equals(event.eventType())) {
            return;
        }

        Notification notification = new Notification();
        notification.setFamilyId(event.familyId());
        notification.setUserId(event.userId());
        notification.setType(event.eventType());
        notification.setTitle("Vượt ngân sách tháng " + event.periodMonth());
        notification.setMessage(String.format(
                "Danh mục #%d đã chi %s / giới hạn %s trong tháng %s",
                event.categoryId(), event.totalSpent(), event.limitAmount(), event.periodMonth()));
        notification.setPayloadJson(toJson(event));
        notification.setIsRead(false);

        notificationDao.insert(notification);
    }

    private String toJson(ExpenseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được ExpenseEvent", e);
        }
    }
}
