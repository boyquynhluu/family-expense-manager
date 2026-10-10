package com.family.expensemanager.gateway.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Defaults for the per-service circuit breakers on the gateway routes (GatewayRoutesConfig — one breaker per
 * downstream service, so one failing service never trips the others).
 *
 * <p>A breaker opens when at least half of the last 20 calls (min. 10) failed — connection refused, timeout,
 * no instance registered, or a 502/503/504 answer — and then fails calls to that service fast with the
 * fallback 503 (FallbackController) for 10s instead of letting each one wait for its timeout; afterwards 3
 * trial calls decide whether it closes again. Plain 4xx/500 answers are normal application responses and do
 * not count.
 *
 * <p>The time limiter is set just above the gateway's HTTP read-timeout ({@code spring.cloud.gateway.mvc.
 * http-client.read-timeout}), so the HTTP timeout always fires first: Resilience4j's default limit is 1s,
 * which would have cut off every slower request (reports, Excel import/export).
 *
 * @author boyquynhluu
 */
@Configuration
public class CircuitBreakerConfiguration {

    private static final Duration TIME_LIMITER_MARGIN = Duration.ofSeconds(5);

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer(
            @Value("${spring.cloud.gateway.mvc.http-client.read-timeout:60s}") Duration readTimeout) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(readTimeout.plus(TIME_LIMITER_MARGIN))
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreakerConfig)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }
}
