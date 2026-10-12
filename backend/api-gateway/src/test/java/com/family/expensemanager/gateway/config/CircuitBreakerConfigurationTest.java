package com.family.expensemanager.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

class CircuitBreakerConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void defaults_failFastAfterHalfOfTheLastCalls_andTimeLimitAboveTheHttpReadTimeout() {
        Resilience4JCircuitBreakerFactory factory = mock(Resilience4JCircuitBreakerFactory.class);

        new CircuitBreakerConfiguration().defaultCircuitBreakerCustomizer(Duration.ofSeconds(60)).customize(factory);

        ArgumentCaptor<Function<String, Resilience4JCircuitBreakerConfiguration>> defaults =
                ArgumentCaptor.forClass(Function.class);
        verify(factory).configureDefault(defaults.capture());
        Resilience4JCircuitBreakerConfiguration config = defaults.getValue().apply("expense-service");

        CircuitBreakerConfig breaker = config.getCircuitBreakerConfig();
        assertThat(breaker.getSlidingWindowSize()).isEqualTo(20);
        assertThat(breaker.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(breaker.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(breaker.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        // Must exceed the 60s HTTP read-timeout so slow requests are not cut off by Resilience4j's 1s default.
        assertThat(config.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(65));
    }
}
