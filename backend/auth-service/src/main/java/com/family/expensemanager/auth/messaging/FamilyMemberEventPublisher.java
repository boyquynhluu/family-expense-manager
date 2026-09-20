package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.FamilyMemberEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link FamilyMemberEvent}s raised (via {@code ApplicationEventPublisher})
 * from within {@code AuthService.acceptInvite()}/{@code leaveFamily()}/{@code removeMember()},
 * and only actually sends to Kafka {@code AFTER_COMMIT} — same pattern as
 * {@link FamilyInviteEventPublisher}.
 */
@Component
public class FamilyMemberEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public FamilyMemberEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                       @Value("${kafka.topic.family-member-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFamilyMemberEvent(FamilyMemberEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.familyId()), event);
    }
}
