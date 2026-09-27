package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.FamilyMemberEvent;
import com.family.expensemanager.common.event.KafkaSendLogging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@link FamilyMemberEvent}s raised (via {@code ApplicationEventPublisher})
 * from within {@code AuthService.acceptInvite()}/{@code leaveFamily()}/{@code removeMember()},
 * and only actually sends to Kafka {@code AFTER_COMMIT} — same pattern as
 * {@link FamilyInviteEventPublisher}.
 */
@Component
@Slf4j(topic = "FamilyMemberEventPublisher")
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
        String key = String.valueOf(event.familyId());
        KafkaSendLogging.logOnFailure(kafkaTemplate.send(topic, key, event), log, topic, key, event);
    }
}
