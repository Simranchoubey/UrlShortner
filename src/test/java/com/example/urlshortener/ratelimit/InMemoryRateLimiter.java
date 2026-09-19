package com.example.urlshortener.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link RateLimiter} used only under the {@code test} profile.
 *
 * <p>Docker/Redis is not assumed available on developer machines, so this
 * deterministic double substitutes for {@link RedisRateLimiter} so web tests can
 * exercise the full fixed-window limiting flow (increment, limit enforcement via
 * {@link RateLimitService}, {@code 429} responses) without a real server. Counters
 * are kept per identifier; the window is a {@link Duration} stored alongside the
 * count and is <em>not</em> advanced by wall-clock here — the service layer is
 * responsible for window semantics, and this simply tracks keyed counts.
 *
 * <p>It is intentionally absent from the production classpath (mirrors
 * {@link com.example.urlshortener.cache.InMemoryUrlCache}). Production always uses
 * {@link RedisRateLimiter}.
 */
@Component
@Profile("test")
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long incrementAndGet(String identifier, Duration window) {
        return counters.computeIfAbsent(identifier, k -> new AtomicLong()).incrementAndGet();
    }

    /** Clears all counters (call between tests to isolate state). */
    public void reset() {
        counters.clear();
    }
}