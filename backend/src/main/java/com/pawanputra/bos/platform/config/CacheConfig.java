package com.pawanputra.bos.platform.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction. The provider (Redis in every deployed environment) and its
 * key prefix / TTL are configured under {@code spring.cache} so code never talks to Redis directly for caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
