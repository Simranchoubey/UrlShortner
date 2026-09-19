package com.example.urlshortener.dto;

import com.example.urlshortener.domain.Url;
import java.time.Instant;

/**
 * Response body for a user's own short URL in the management APIs:
 * {@code GET /api/v1/urls} (list items) and {@code GET /api/v1/urls/{id}}.
 *
 * <p>A DTO rather than the {@code Url} entity — entities are never exposed
 * directly through REST. It exposes only the fields a client needs and never any
 * internal database/security details.
 *
 * @param id           the URL's database id
 * @param shortCode    the unique short code or custom alias used in the path
 * @param shortUrl     the fully-qualified short URL (from configured base URL)
 * @param originalUrl  the long URL that was shortened
 * @param customAlias  the caller-chosen alias, if one was provided
 * @param expiresAt    the expiry timestamp, or {@code null} if it never expires
 * @param createdAt    when the URL was created
 */
public record UrlResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        String customAlias,
        Instant expiresAt,
        Instant createdAt) {

    /** Builds a management response from a persisted URL row and the public base URL. */
    public static UrlResponse from(Url url, String baseUrl) {
        String cleanBase = trimTrailingSlash(baseUrl);
        return new UrlResponse(
                url.getId(),
                url.getShortCode(),
                cleanBase + "/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getCustomAlias(),
                (url.getExpiresAt() == null) ? null : url.getExpiresAt().toInstant(),
                (url.getCreatedAt() == null) ? null : url.getCreatedAt().toInstant());
    }

    private static String trimTrailingSlash(String baseUrl) {
        return (baseUrl != null && baseUrl.endsWith("/"))
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}