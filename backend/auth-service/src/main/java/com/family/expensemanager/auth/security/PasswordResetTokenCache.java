package com.family.expensemanager.auth.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Caches the RAW value of a still-live password-reset token, keyed by userId. {@code
 * USERS.reset_password_token} only ever stores its SHA-256 hash (like every other bearer
 * token in this codebase, refresh tokens included) — this cache exists purely so {@code
 * AuthService#forgotPassword} can still resend the exact same link while the previous one
 * is valid (its own javadoc explains why that matters) without keeping the raw, usable
 * token sitting in MySQL.
 *
 * A cache miss (Redis restarted, evicted under memory pressure...) is not a correctness
 * bug: {@code forgotPassword} just falls back to minting a brand-new token, same as if the
 * previous one had actually expired — the one downside is the previous link silently stops
 * working a little early, which is the same behaviour this whole feature already tolerates
 * for a token that's genuinely about to expire.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCache {

    private static final String KEY_PREFIX = "password-reset-raw:";
    private static final String EMAIL_SLOT_KEY_PREFIX = "password-reset-email-slot:";

    private final StringRedisTemplate redisTemplate;

    public void put(Long userId, String rawToken, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, rawToken, ttl);
    }

    public Optional<String> get(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    public void evict(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    /**
     * Claims the right to send this user one reset email within {@code cooldown} (atomic SET NX + TTL).
     * Returns false while an earlier claim is still live — i.e. an email already went out in this window.
     */
    public boolean tryAcquireEmailSlot(Long userId, Duration cooldown) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(EMAIL_SLOT_KEY_PREFIX + userId, "1", cooldown));
    }
}
