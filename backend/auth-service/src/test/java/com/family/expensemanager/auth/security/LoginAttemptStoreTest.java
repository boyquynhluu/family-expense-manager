package com.family.expensemanager.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginAttemptStoreTest {

    private static final String KEY = "login-fail:a@b.com";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginAttemptStore store;

    @BeforeEach
    void setUp() {
        store = new LoginAttemptStore(redisTemplate);
    }

    @Test
    void recordFailure_startsWindowOnFirstFailure_usingLowercasedTrimmedEmail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(1L);

        store.recordFailure("  A@B.com ");

        verify(redisTemplate).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    void recordFailure_restartsWindowWhenLockoutThresholdReached_butNotInBetween() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(3L, 5L);

        store.recordFailure("a@b.com");
        verify(redisTemplate, never()).expire(any(), any(Duration.class));

        store.recordFailure("a@b.com");
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    void remainingLockMinutes_isZero_belowThresholdOrWhenNoCounter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(null, "4");

        assertThat(store.remainingLockMinutes("a@b.com")).isZero();
        assertThat(store.remainingLockMinutes("a@b.com")).isZero();
    }

    @Test
    void remainingLockMinutes_roundsRemainingSecondsUpToWholeMinutes_whenLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("5");
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(601L, 20L);

        assertThat(store.remainingLockMinutes("a@b.com")).isEqualTo(11);
        assertThat(store.remainingLockMinutes("a@b.com")).isEqualTo(1);
    }

    @Test
    void reset_deletesCounter() {
        store.reset("A@b.com");

        verify(redisTemplate).delete(KEY);
    }
}
