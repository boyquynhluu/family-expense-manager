package com.family.expensemanager.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Short-lived server-side state for the "password OK, waiting on the 2FA code" step of
 * login (README "9. Không có 2FA") — deliberately not a JWT, since this token must not
 * be usable as a bearer token against any other endpoint. Redis (already wired for
 * RevokedSessionStore) is a natural fit: the challenge only needs to survive a couple of
 * minutes while the user types their code.
 */
@Component
@RequiredArgsConstructor
public class TwoFactorChallengeStore {

    private static final String KEY_PREFIX = "2fa-challenge:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issueChallenge(Long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
        return token;
    }

    /** Single-use: the challenge is deleted whether or not the caller goes on to use the userId. */
    public Optional<Long> consumeChallenge(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String key = KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }
}
