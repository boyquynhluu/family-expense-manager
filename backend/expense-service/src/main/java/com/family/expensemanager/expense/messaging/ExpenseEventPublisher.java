package com.family.expensemanager.expense.messaging;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.event.KafkaSendLogging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@link ExpenseEvent}s raised (via {@code ApplicationEventPublisher}) from
 * within a {@code @Transactional} service method, and only actually sends to Kafka
 * {@code AFTER_COMMIT} — see README "Hợp đồng Kafka" ("publish sau khi transaction DB đã commit").
 * If the transaction rolls back, nothing is published.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "ExpenseEventPublisher")
public class ExpenseEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public ExpenseEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                  @Value("${kafka.topic.expense-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    // fallbackExecution: recurring-rule events are published from the scheduler, outside any transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExpenseEvent(ExpenseEvent event) {
        String key = String.valueOf(event.familyId());
        KafkaSendLogging.logOnFailure(kafkaTemplate.send(topic, key, event), log, topic, key, event);
    }
}
