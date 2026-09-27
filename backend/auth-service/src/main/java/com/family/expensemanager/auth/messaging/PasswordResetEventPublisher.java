package com.family.expensemanager.auth.messaging;

import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.KafkaSendLogging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@link PasswordResetEvent}s raised (via {@code ApplicationEventPublisher})
 * from within {@code AuthService.forgotPassword()}, and only actually sends to Kafka
 * {@code AFTER_COMMIT} — same pattern as {@link UserVerificationEventPublisher}.
 */
@Component
@Slf4j(topic = "PasswordResetEventPublisher")
public class PasswordResetEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public PasswordResetEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                        @Value("${kafka.topic.password-reset}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetEvent(PasswordResetEvent event) {
        String key = String.valueOf(event.userId());
        KafkaSendLogging.logOnFailure(kafkaTemplate.send(topic, key, event), log, topic, key, event);
    }
}
