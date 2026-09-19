package com.example.urlshortener.ratelimit;

/**
 * Logical rate-limit scopes, used to build a distinct key namespace per endpoint
 * so counters for different endpoints never interfere with each other (Phase 9).
 *
 * <p>The {@link #keySegment()} is embedded in the Redis key, e.g.
 * {@code rate-limit:create-url:{id}}. Separate scopes have separate counters, so
 * a user who hits a high {@code create-url} limit is not penalised on, say,
 * {@code auth-login}.
 */
public enum RateLimitScope {

    /** {@code POST /api/v1/urls} — per authenticated user id. */
    CREATE_URL("create-url"),

    /** {@code POST /api/v1/auth/login} — per client IP. */
    AUTH_LOGIN("auth-login"),

    /** {@code POST /api/v1/auth/register} — per client IP. */
    AUTH_REGISTER("auth-register"),

    /** {@code GET /{shortCode}} — per client IP (disabled by default; §12). */
    REDIRECT("redirect");

    private final String keySegment;

    RateLimitScope(String keySegment) {
        this.keySegment = keySegment;
    }

    public String keySegment() {
        return keySegment;
    }
}