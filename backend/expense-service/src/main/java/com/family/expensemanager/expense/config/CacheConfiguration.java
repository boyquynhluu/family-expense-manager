package com.family.expensemanager.expense.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Makes cache keys come out exactly as documented in README ("Redis"):
 * {@code expense:summary:{familyId}:{yearMonth}} / {@code expense:report:category:{familyId}:{yearMonth}}
 * — Spring's default cache-name/key separator is "::"; this switches it to a single ":".
 */
@EnableCaching
@Configuration
public class CacheConfiguration {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder.cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig()
                        .computePrefixWith(cacheName -> cacheName + ":"));
    }
}
