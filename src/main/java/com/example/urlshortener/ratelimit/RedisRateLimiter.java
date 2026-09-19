package com.example.urlshortener.ratelimit;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed, fixed-window {@link RateLimiter} (Phase 9). Production
 * implementation, active only when the {@code test} profile is not enabled (the
 * {@code test} profile substitutes an in-memory limiter, so only one
 * {@code RateLimiter} bean ever exists).
 *
 * <p>The counter is stored under a rate-limit-specific key namespace —
 * {@code rate-limit:{scope}:{identifier}} (built by {@link RateLimitService}) —
 * fully independent of the URL cache keys ({@code url:{shortCode}}), so rate
 * limiting never touches or disturbs the Phase 5 caching. The increment is atomic
 * ({@code INCR} via {@link StringRedisTemplate}) and the window expiry is
 * applied/maintained as part of the same call, so concurrent requests are handled
 * correctly without a Lua script or a token bucket.
 *
 * <p><strong>Fail-open policy (§8):</strong> any Redis failure is caught and logged
 * (WARN) and the call degrades to "allow", so a Redis outage never turns a normal
 * API request into a {@code 500}. This mirrors the treatment of the URL cache.
 */
@Component
@Profile("!test")
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementAndGet(String identifier, Duration window) {
        try {
            Long count = redis.opsForValue().increment(identifier);
            // First request in a window establishes its expiry. Only branch when we
            // created the key (count == 1) so we never reset the window mid-way.
            if (count != null && count == 1L) {
                redis.expire(identifier, window);
            }
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            // Fail open: a Redis outage must not break the API. Log, don't rethrow.
            log.warn("Redis rate-limit operation failed for '{}'; failing open and allowing request: {}",
                    identifier, ex.getMessage());
            return 0L;
        }
    }
}