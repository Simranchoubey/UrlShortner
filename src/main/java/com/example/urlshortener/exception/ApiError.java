package com.example.urlshortener.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Standard, consistent error response body returned by
 * {@link GlobalExceptionHandler} for every handled failure. Using a single
 * format keeps error responses uniform across all endpoints (§16 of the plan).
 *
 * <p>When present, {@link #errors()} carries per-field validation messages
 * (key = field name, value = human-readable problem). {@code message} is always
 * populated and {@code errors} may be empty.
 *
 * @param timestamp when the error occurred (UTC)
 * @param status    the HTTP status code
 * @param error     the HTTP reason phrase
 * @param message   a human-readable description
 * @param errors    optional per-field validation errors
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> errors) {

    /** Builds a simple single-message error without field detail. */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    /** Builds an error that carries per-field validation messages. */
    public static ApiError of(int status, String error, String message, Map<String, String> errors) {
        return new ApiError(Instant.now(), status, error, message, errors);
    }
}