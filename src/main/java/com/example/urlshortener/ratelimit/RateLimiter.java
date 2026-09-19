package com.example.urlshortener.ratelimit;

import java.time.Duration;

/**
 * Low-level fixed-window counter for one rate-limit key (Phase 9).
 *
 * <p>Implementations {@link #incrementAndGet(String, Duration)} a per-identifier
 * counter and apply/maintain the window's expiry. The returned count is compared
 * against a configured limit to decide whether to allow or reject a request. The
 * counter sits in a rate-limit-specific key namespace, fully independent of the
 * URL cache keys (never {@code url:{...}}).
 *
 * <p>{@link RedisRateLimiter} is the production, Redis-backed implementation;
 * {@link InMemoryRateLimiter} replaces it under the {@code test} profile so
 * web tests exercise the full limiting flow deterministically without a server.
 */
public interface RateLimiter {

    /**
     * Atomically increments the counter for an identifier and applies/maintains
     * the window expiry. Returns the new count for the current window.
     *
     * <p>Must be safe under concurrent requests (atomic increment), and must
     * <strong>fail open</strong> — if the backing store is unavailable it logs a
     * warning and returns {@code 0}, allowing the request to proceed rather than
     * turning a normal API call into a {@code 500}.
     *
     * @param identifier the rate-limit key (namespace + scope + identity)
     * @param window     the fixed window duration
     * @return the current count for this identifier in this window
     */
    long incrementAndGet(String identifier, Duration window);
}