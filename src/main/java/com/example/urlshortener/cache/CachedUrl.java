package com.example.urlshortener.cache;

import com.example.urlshortener.domain.Url;
import java.time.Instant;

/**
 * The value cached in Redis for a short code (key {@code url:{shortCode}}).
 *
 * <p>Contains only what is needed to perform a redirect safely (§8 of the plan):
 * the original destination URL and its expiry instant. {@code expiresAt} is
 * {@code null} for non-expiring links.
 *
 * <p>It is serialized as a small, human-readable JSON object (e.g.
 * {@code {"originalUrl":"https://…","expiresAt":"2035-06-01T00:00:00Z"}}) so it can
 * be inspected during development. No heavy database fields are cached.
 *
 * @param originalUrl the destination URL the client should be redirected to
 * @param expiresAt   the instant past which the link is expired, or {@code null}
 *                    if the link never expires
 */
public record CachedUrl(String originalUrl, Instant expiresAt) {

    /** Builds a cache value from a persisted URL row. */
    public static CachedUrl from(Url url) {
        return new CachedUrl(
                url.getOriginalUrl(),
                (url.getExpiresAt() == null) ? null : url.getExpiresAt().toInstant());
    }
}