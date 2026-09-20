package com.family.expensemanager.common.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared Redis blocklist so an explicitly revoked login session (README "8. Không quản
 * lý được phiên đăng nhập") stops working immediately, instead of waiting for its
 * already-issued access token to expire on its own. Every service that verifies JWTs
 * ({@link JwtAuthenticationFilter}) checks this before trusting a token's claims.
 *
 * TTL on a revoked entry is capped at the access-token lifetime: once that long has
 * passed the token would have expired naturally anyway, so there's no need to remember
 * the revocation any longer.
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "RevokedSessionStore")
public class RevokedSessionStore {

    private static final String KEY_PREFIX = "revoked-session:";

    private final StringRedisTemplate redisTemplate;

    public void markRevoked(Long sessionId, long ttlMillis) {
        if (sessionId == null || ttlMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, "1", Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.warn("Không ghi được revoked-session vào Redis, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Fails open (returns false) on Redis errors — an outage of the blocklist store
     * should not take down authentication for every service; the session will simply
     * remain revocable-but-usable until its access token expires naturally, same as
     * before this feature existed.
     */
    public boolean isRevoked(Long sessionId) {
        if (sessionId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + sessionId));
        } catch (Exception e) {
            log.warn("Không kiểm tra được revoked-session từ Redis, sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }
}
