package com.family.expensemanager.auth.security;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Consecutive failed-login counter per email, in Redis: the 5th failure inside the window
 * locks the email out for the same window. Unknown emails are counted too, so the lockout
 * does not reveal which emails have an account.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptStore {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private static final String KEY_PREFIX = "login-fail:";

    private final StringRedisTemplate redisTemplate;

    public void recordFailure(String email) {
        String key = key(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && (count == 1 || count == MAX_ATTEMPTS)) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    public void reset(String email) {
        redisTemplate.delete(key(email));
    }

    /** Whole minutes (rounded up, at least 1) until the lockout ends, or 0 when the email is not locked out. */
    public long remainingLockMinutes(String email) {
        String key = key(email);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || Long.parseLong(value) < MAX_ATTEMPTS) {
            return 0;
        }
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds == -2) {
            return 0;
        }
        if (ttlSeconds == -1) {
            redisTemplate.expire(key, WINDOW);
            return WINDOW.toMinutes();
        }
        return Math.max(1, (ttlSeconds + 59) / 60);
    }

    private static String key(String email) {
        return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }
}
