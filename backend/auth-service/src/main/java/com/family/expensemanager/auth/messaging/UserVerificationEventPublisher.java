package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.UserVerificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link UserVerificationEvent}s raised (via {@code ApplicationEventPublisher})
 * from within {@code AuthService.register()}, and only actually sends to Kafka
 * {@code AFTER_COMMIT} so the email is never sent for a registration that rolled back
 * — same pattern as expense-service's {@code ExpenseEventPublisher}.
 */
@Component
public class UserVerificationEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public UserVerificationEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                           @Value("${kafka.topic.user-verification}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserVerificationEvent(UserVerificationEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.userId()), event);
    }
}
