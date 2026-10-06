package com.family.expensemanager.notification.dto;

import com.family.expensemanager.notification.domain.entity.Notification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    void linkPath_comesFromTheStoredEvent_andTheEventItselfStaysHidden() {
        NotificationResponse response = NotificationResponse.from(
                notification("LOAN_CREATED", "{\"userEmail\":\"me@b.com\",\"linkPath\":\"/loans\"}"));

        assertThat(response.linkPath()).isEqualTo("/loans");
        assertThat(response.toString()).doesNotContain("me@b.com");
    }

    @Test
    void olderTypesWithoutALink_getTheirPageByType_andUnknownOnesGetNone() {
        assertThat(NotificationResponse.from(notification("BUDGET_EXCEEDED", "{}")).linkPath()).isEqualTo("/budgets");
        assertThat(NotificationResponse.from(notification("EXPENSE_DELETED", null)).linkPath()).isEqualTo("/trash");
        assertThat(NotificationResponse.from(notification("SOMETHING_NEW", "not json")).linkPath()).isNull();
    }

    @Test
    void onlyInAppPaths_areFollowed() {
        assertThat(NotificationResponse.from(notification("LOAN_CREATED", "{\"linkPath\":\"https://evil.example\"}"))
                .linkPath()).isNull();
        assertThat(NotificationResponse.from(notification("LOAN_CREATED", "{\"linkPath\":\"//evil.example\"}"))
                .linkPath()).isNull();
    }

    private static Notification notification(String type, String payloadJson) {
        Notification n = new Notification();
        n.setId(1L);
        n.setFamilyId(1L);
        n.setUserId(7L);
        n.setType(type);
        n.setTitle("t");
        n.setMessage("m");
        n.setIsRead(false);
        n.setPayloadJson(payloadJson);
        return n;
    }
}
