package com.example.urlshortener.dto;

import java.time.Instant;

/**
 * Response body for a successfully created short URL ({@code 201 Created}).
 *
 * <p>A DTO rather than the {@code Url} entity — entities are never exposed
 * directly through REST.
 *
 * @param shortCode  the unique short code or custom alias used in the path
 * @param shortUrl   the fully-qualified short URL (from configured BASE_URL)
 * @param originalUrl the long URL that was shortened
 * @param customAlias the caller-chosen alias, if one was provided
 * @param expiresAt  the expiry timestamp, if one was set (null = never expires)
 */
public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        String customAlias,
        Instant expiresAt) {
}