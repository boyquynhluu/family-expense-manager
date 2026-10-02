package com.family.expensemanager.common.event;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

/**
 * Every publisher in this codebase calls {@code KafkaTemplate.send(...)} fire-and-forget and, until now,
 * never looked at the returned future — a publish failure (broker unreachable past the producer's own
 * internal retry window, serialization error...) was completely silent: no log line, nothing. The event
 * (an email, an in-app notification, an admin alert...) was simply lost with no trace anywhere.
 *
 * This does not add retrying — the producer's own internal retry (idempotent producer, bounded by
 * {@code delivery.timeout.ms}) already absorbs brief broker outages on its own. It only makes a genuine,
 * final failure visible in the logs instead of vanishing without one.
 *
 * @author boyquynhluu
 */
public final class KafkaSendLogging {

    private KafkaSendLogging() {
    }

    public static void logOnFailure(CompletableFuture<?> sendFuture, Logger log, String topic, Object key, Object event) {
        sendFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Không publish được lên Kafka, topic={}, key={}, event={}", topic, key, event, ex);
            }
        });
    }
}
