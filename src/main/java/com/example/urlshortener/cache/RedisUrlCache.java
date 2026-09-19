package com.example.urlshortener.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed {@link UrlCache} using a cache-aside pattern (§8 of the plan).
 *
 * <p>Keys are {@code url:{shortCode}} and values are the JSON serialization of a
 * {@link CachedUrl} (see {@link CachedUrl}). TTL is
 * {@code min(expiresAt - now, configured maximum)} for expiring links and the
 * configured maximum (default 7 days) for links that never expire, so stale data
 * never lives forever.
 *
 * <p>Redis is strictly optional here. Every read/write is guarded so that a Redis
 * outage only degrades to a database round-trip — it never causes a redirect or a
 * URL creation to fail. Failures are logged (WARN) but never rethrown to callers.
 * A normal cache miss is <em>not</em> logged as an error (§17 of the plan).
 *
 * <p>Disabled under the {@code test} profile, where an in-memory implementation
 * is substituted so integration tests run without a real Redis server.
 */
@Component
@Profile("!test")
public class RedisUrlCache implements UrlCache {

    private static final Logger log = LoggerFactory.getLogger(RedisUrlCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String keyPrefix;
    private final Duration defaultTtl;

    public RedisUrlCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.cache.key-prefix}") String keyPrefix,
            @Value("${app.cache.url-ttl}") Duration defaultTtl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.keyPrefix = keyPrefix;
        this.defaultTtl = defaultTtl;
    }

    @Override
    public Optional<CachedUrl> get(String shortCode) {
        String raw;
        try {
            raw = redis.opsForValue().get(key(shortCode));
        } catch (RuntimeException ex) {
            // Redis read failure → report a miss so the caller falls back to DB.
            log.warn("Redis cache read failed for shortCode '{}'; falling back to database: {}",
                    shortCode, ex.getMessage());
            return Optional.empty();
        }

        if (raw == null) {
            return Optional.empty(); // normal miss — not an error (§17)
        }

        try {
            return Optional.of(objectMapper.readValue(raw, CachedUrl.class));
        } catch (JsonProcessingException ex) {
            log.warn("Cached value for shortCode '{}' is not valid JSON; treating as miss: {}",
                    shortCode, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, CachedUrl value) {
        Duration ttl = ttlFor(value.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            // Already expired — nothing useful to cache.
            return;
        }
        try {
            redis.opsForValue().set(key(shortCode), objectMapper.writeValueAsString(value),
                    ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException | JsonProcessingException ex) {
            // Cache write failure must never block URL creation/redirect.
            log.warn("Redis cache write failed for shortCode '{}'; skipping cache: {}",
                    shortCode, ex.getMessage());
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redis.delete(key(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Redis cache evict failed for shortCode '{}': {}", shortCode, ex.getMessage());
        }
    }

    private String key(String shortCode) {
        return keyPrefix + shortCode;
    }

    /**
     * TTL strategy (§8): {@code min(expiresAt - now, configured maximum)} for
     * expiring links; the configured maximum (7 days by default) when the link
     * never expires. Uses the injectable {@link Clock} so it is deterministic in
     * tests.
     */
    private Duration ttlFor(Instant expiresAt) {
        Instant now = clock.instant();
        if (expiresAt == null) {
            return defaultTtl;
        }
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.compareTo(defaultTtl) > 0) {
            return defaultTtl;
        }
        return remaining;
    }
}