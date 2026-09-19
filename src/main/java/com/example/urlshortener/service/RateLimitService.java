package com.example.urlshortener.service;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.ratelimit.RateLimiter;
import com.example.urlshortener.ratelimit.RateLimitScope;
import java.time.Duration;

/**
 * Coordinates Redis-backed rate limiting (Phase 9).
 *
 * <p>This is the single entry point the {@code RateLimitFilter} calls: it builds a
 * rate-limit key from a {@link RateLimitScope} and an identifier, atomically
 * increments the counter, and compares it against the configured limit. When the
 * limit is exceeded it throws {@link RateLimitExceededException}, which the global
 * exception handler maps to a clean {@code 429} + {@code Retry-After}.
 *
 * <p>Controllers and business logic stay free of Redis/rate-limit details; only
 * this service knows how keys and limits are derived. Key namespaces are
 * {@code rate-limit:{scope}:{identifier}} — never {@code url:{...}} — so rate
 * limiting is fully independent of the Phase 5 URL cache.
 *
 * <p>A distributed lock is not needed: the underlying {@link RateLimiter} performs
 * an atomic increment, so concurrent requests are handled correctly.
 */
public class RateLimitService {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitService(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * Increments the counter for a scope+identifier and enforces the given limit.
     *
     * <p>If rate limiting is globally disabled, this is a no-op (request allowed).
     * Otherwise, once the count within the window reaches {@code limit + 1} the
     * request is rejected with a {@link RateLimitExceededException} carrying the
     * remaining seconds of the window for the {@code Retry-After} header.
     *
     * @param scope       the endpoint being rate-limited
     * @param identifier  the requesting identity (user id or client IP)
     * @param limit       the maximum allowed requests per window
     * @param window      the fixed window duration
     */
    public void check(RateLimitScope scope, String identifier, int limit, Duration window) {
        if (!properties.enabled()) {
            return; // master switch off → allow everything.
        }

        long count = rateLimiter.incrementAndGet(key(scope, identifier), window);
        if (count > limit) {
            int retryAfter = (int) Math.max(1, window.toSeconds());
            throw new RateLimitExceededException(
                    "Too many requests. Please retry after the current window has elapsed.",
                    retryAfter);
        }
    }

    /**
     * Builds the full rate-limit key: {@code rate-limit:{scope}:{identifier}}.
     *
     * @param scope      the endpoint scope
     * @param identifier the requesting identity
     * @return the Redis key, namespaced away from URL cache keys
     */
    public String key(RateLimitScope scope, String identifier) {
        return properties.prefix() + scope.keySegment() + ":" + identifier;
    }
}