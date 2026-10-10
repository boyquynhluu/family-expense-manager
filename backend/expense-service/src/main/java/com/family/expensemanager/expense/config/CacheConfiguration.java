package com.family.expensemanager.expense.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * Makes cache keys come out exactly as documented in README ("Redis"):
 * {@code expense:summary:{familyId}:{yearMonth}} / {@code expense:report:category:{familyId}:{yearMonth}}
 * — Spring's default cache-name/key separator is "::"; this switches it to a single ":".
 *
 * @author boyquynhluu
 */
@Slf4j(topic = "CacheConfiguration")
@EnableCaching
@Configuration
public class CacheConfiguration implements CachingConfigurer {

    /**
     * Declaring this bean makes Spring Boot use it as the cache defaults instead of building them from
     * spring.cache.redis.*, so the TTL from application.yml is applied here (the previous customizer replaced
     * the defaults with a TTL-less config, so entries never expired).
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> cacheName + ":");
        Duration ttl = cacheProperties.getRedis().getTimeToLive();
        return ttl != null ? config.entryTtl(ttl) : config;
    }

    /** Picked up through CachingConfigurer — a CacheErrorHandler bean on its own is ignored by Spring. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache GET failed, reading the database instead. cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed. cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("Redis cache EVICT failed, entry stays until its TTL. cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("Redis cache CLEAR failed. cache={}", cache.getName(), e);
            }
        };
    }

}
