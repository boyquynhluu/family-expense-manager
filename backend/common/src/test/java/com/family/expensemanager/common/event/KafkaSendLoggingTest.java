package com.family.expensemanager.common.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class KafkaSendLoggingTest {

    @Test
    void logOnFailure_logsError_whenTheFutureCompletesExceptionally() {
        Logger log = mock(Logger.class);
        CompletableFuture<Object> future = new CompletableFuture<>();

        KafkaSendLogging.logOnFailure(future, log, "expense-events", "42", "some-event");
        future.completeExceptionally(new RuntimeException("broker unreachable"));

        verify(log, timeout(1000)).error(anyString(), eq("expense-events"), eq("42"), eq("some-event"), any(Throwable.class));
    }

    @Test
    void logOnFailure_logsNothing_whenTheSendSucceeds() {
        Logger log = mock(Logger.class);
        CompletableFuture<Object> future = new CompletableFuture<>();

        KafkaSendLogging.logOnFailure(future, log, "expense-events", "42", "some-event");
        future.complete(new Object());

        verify(log, never()).error(anyString(), any(), any(), any(), any(Throwable.class));
    }
}
