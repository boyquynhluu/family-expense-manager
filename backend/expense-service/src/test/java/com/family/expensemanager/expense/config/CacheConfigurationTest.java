package com.family.expensemanager.expense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

class CacheConfigurationTest {

    private final CacheConfiguration configuration = new CacheConfiguration();

    @Test
    void cacheDefaults_keepTheTtlFromProperties_andASingleColonPrefix() {
        CacheProperties properties = new CacheProperties();
        properties.getRedis().setTimeToLive(Duration.ofMinutes(10));

        RedisCacheConfiguration config = configuration.redisCacheConfiguration(properties);

        assertThat(config.getTtlFunction().getTimeToLive("1:2026-10", "value")).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.getKeyPrefixFor("expense:summary")).isEqualTo("expense:summary:");
    }

    @Test
    void withoutTtlProperty_entriesDoNotExpire() {
        RedisCacheConfiguration config = configuration.redisCacheConfiguration(new CacheProperties());

        assertThat(config.getTtlFunction().getTimeToLive("key", "value")).isEqualTo(Duration.ZERO);
    }

    @Test
    void redisErrors_areLoggedAndSwallowed_soTheMethodFallsBackToTheDatabase() {
        CacheErrorHandler handler = configuration.errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("expense:summary");
        RuntimeException redisDown = new IllegalStateException("redis down");

        assertThatCode(() -> {
            handler.handleCacheGetError(redisDown, cache, "1:2026-10");
            handler.handleCachePutError(redisDown, cache, "1:2026-10", "value");
            handler.handleCacheEvictError(redisDown, cache, "1:2026-10");
            handler.handleCacheClearError(redisDown, cache);
        }).doesNotThrowAnyException();
    }
}
