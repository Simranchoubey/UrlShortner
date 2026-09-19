package com.example.urlshortener.messaging;

import java.time.OffsetDateTime;

/**
 * Message published to the Kafka click-events topic for a single redirect.
 *
 * <p>This is an independent transport DTO — it deliberately mirrors, but is not,
 * the JPA {@link com.example.urlshortener.domain.ClickEvent} entity. Keeping the
 * published shape separate from the database entity lets the async transport
 * evolve without leaking persistence concerns onto the wire (and vice-versa), and
 * means the entity's {@code @PrePersist} timestamp defaulting etc. stay inside the
 * persistence layer.
 *
 * <p>{@code shortCode} is the primary identifier. {@code urlId} is populated on the
 * database path (where the redirect already loaded the {@code Url}); on a pure
 * cache hit the consumer resolves the id from {@code shortCode}, so the redirect
 * hot path never issues an extra database query just to record a click. The
 * consuming side enforces the {@code click_events.url_id} NOT NULL foreign key.
 *
 * @param urlId     the owning URL's database id when known; {@code null} on a cache
 *                  hit (the consumer resolves it from {@code shortCode})
 * @param shortCode the short code that was actually hit (denormalized for reporting)
 * @param eventTime the time the redirect happened (UTC)
 * @param referrer  the {@code Referer} header, if present; nullable
 * @param userAgent the {@code User-Agent} header, if present; nullable
 * @param ip        the client IP, if determinable; nullable
 * @param country   two-letter country code when known; always {@code null} in this
 *                  phase (no geolocation) per IMPLEMENTATION_PLAN.md §8/§5
 */
public record ClickEventMessage(
        Long urlId,
        String shortCode,
        OffsetDateTime eventTime,
        String referrer,
        String userAgent,
        String ip,
        String country) {

    /**
     * Validation is intentionally light: {@code shortCode} is required, everything
     * else (including {@code urlId}, which may be unknown on a cache hit) is
     * optional. The consumer is responsible for resolving/filtering records that
     * cannot be mapped to a real URL.
     */
    public ClickEventMessage {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("shortCode must not be blank");
        }
        // Country is a CHAR(2) in the schema; only a 2-letter value (or null) is valid.
        if (country != null && country.length() != 2) {
            throw new IllegalArgumentException("country must be a two-letter code or null");
        }
    }
}