package com.example.urlshortener.exception;

/**
 * Thrown when a resolved URL has already expired at redirect time.
 * Mapped by {@link GlobalExceptionHandler} to {@code 410 Gone}.
 *
 * <p>Expired URLs are intentionally left in the database (no deletion, no
 * background cleanup introduced in Phase 4); this signals to the caller that the
 * link is permanently unusable rather than simply missing.
 */
public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String message) {
        super(message);
    }
}