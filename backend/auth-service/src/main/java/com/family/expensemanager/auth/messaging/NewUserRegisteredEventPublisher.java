package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.NewUserRegisteredEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link NewUserRegisteredEvent}s to Kafka {@code AFTER_COMMIT}, so admins are never told about
 * a sign-up that rolled back. {@code fallbackExecution} keeps it working when the OAuth2 login flow
 * calls the service outside a transaction.
 */
@Component
public class NewUserRegisteredEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public NewUserRegisteredEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                            @Value("${kafka.topic.user-registered}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewUserRegistered(NewUserRegisteredEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.userId()), event);
    }
}
