package com.example.urlshortener.exception;

/**
 * Thrown when a request exceeds its configured rate limit (Phase 9).
 *
 * <p>Mapped by {@link GlobalExceptionHandler} to {@code 429 Too Many Requests}
 * with the standard {@link ApiError} body and a {@code Retry-After} header
 * derived from the remaining time left in the active window.
 *
 * <p>Never carries internal details (no Redis host, exception trace, or counter
 * values) so the client only ever sees a clean, consistent rate-limit response.
 */
public class RateLimitExceededException extends RuntimeException {

    /** Seconds the client should wait before retrying (for {@code Retry-After}). */
    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}