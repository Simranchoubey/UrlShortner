package com.example.urlshortener.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link UrlCache} used only under the {@code test} profile.
 *
 * <p>Docker/Redis is not assumed available on developer machines, so integration
 * tests substitute this implementation to exercise the full cache-aside flow
 * (get/put/expiry) deterministically without a real Redis server. It mirrors the
 * semantics of {@link RedisUrlCache}: values carry an {@code expiresAt} that is
 * still honoured on reads, and TTL is capped by a configured maximum (summarised
 * so tests can assert the intended TTL). It is intentionally <em>not</em> in the
 * production classpath.
 *
 * <p>Production always uses {@link RedisUrlCache}; this class never runs outside
 * tests, so it does not undermine PostgreSQL as the source of truth.
 */
@Component
@Profile("test")
public class InMemoryUrlCache implements UrlCache {

    private final Map<String, CachedUrl> store = new ConcurrentHashMap<>();
    /** Tracks the TTL that was applied on the last put, keyed by short code. */
    private final Map<String, Duration> ttls = new ConcurrentHashMap<>();

    private final Clock clock;
    private final Duration defaultTtl;

    public InMemoryUrlCache(
            Clock clock,
            @Value("${app.cache.url-ttl}") Duration defaultTtl) {
        this.clock = clock;
        this.defaultTtl = defaultTtl;
    }

    @Override
    public Optional<CachedUrl> get(String shortCode) {
        return Optional.ofNullable(store.get(shortCode));
    }

    @Override
    public void put(String shortCode, CachedUrl value) {
        Instant now = clock.instant();
        if (value.expiresAt() != null && !value.expiresAt().isAfter(now)) {
            // Already expired — nothing useful to cache (mirrors RedisUrlCache).
            return;
        }
        store.put(shortCode, value);
        ttls.put(shortCode, computeTtl(value.expiresAt(), now));
    }

    @Override
    public void evict(String shortCode) {
        store.remove(shortCode);
        ttls.remove(shortCode);
    }

    /** Returns the TTL that was stored for a short code on its last put. */
    public Duration storedTtl(String shortCode) {
        return ttls.get(shortCode);
    }

    public boolean contains(String shortCode) {
        return store.containsKey(shortCode);
    }

    /**
     * Directly places a value into the store, bypassing the expiry guard used by
     * {@link #put}. Used only by tests to simulate a stale entry that already
     * exists in Redis (e.g. its {@code expiresAt} has since passed but the TTL
     * has not yet removed it) — the resolver must still reject it.
     */
    public void seed(String shortCode, CachedUrl value) {
        store.put(shortCode, value);
    }

    private Duration computeTtl(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return defaultTtl;
        }
        Duration remaining = Duration.between(now, expiresAt);
        return (remaining.compareTo(defaultTtl) > 0) ? defaultTtl : remaining;
    }
}