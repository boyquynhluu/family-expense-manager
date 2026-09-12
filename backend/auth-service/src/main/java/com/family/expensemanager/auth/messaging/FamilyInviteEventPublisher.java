package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.FamilyInviteEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link FamilyInviteEvent}s raised (via {@code ApplicationEventPublisher})
 * from within {@code AuthService.inviteMember()}, and only actually sends to Kafka
 * {@code AFTER_COMMIT} — same pattern as {@link UserVerificationEventPublisher}.
 */
@Component
public class FamilyInviteEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public FamilyInviteEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                       @Value("${kafka.topic.family-invite}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFamilyInviteEvent(FamilyInviteEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.familyId()), event);
    }
}
