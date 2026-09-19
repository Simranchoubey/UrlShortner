package com.example.urlshortener.cache;

import java.util.Optional;

/**
 * Small cache abstraction in front of PostgreSQL for the redirect path (§8, §10
 * of the plan). Keeping Redis behind this interface means controllers and the
 * URL service depend only on this contract rather than on a specific cache
 * implementation, and Redis failures can be contained at one boundary.
 *
 * <p>This is intentionally tiny: a get, a put (with an internally-computed TTL),
 * and an evict for the future deletion phase. The cache is a performance layer
 * only — PostgreSQL remains the source of truth.
 */
public interface UrlCache {

    /**
     * Returns the cached value for a short code, or {@link Optional#empty()} on a
     * miss. Implementations must swallow Redis connectivity/read errors and
     * report a miss rather than propagate, so the redirect can fall back to the
     * database.
     *
     * @param shortCode the redirect path segment (key {@code url:{shortCode}})
     */
    Optional<CachedUrl> get(String shortCode);

    /**
     * Stores a value for the short code with an appropriate TTL. If the value has
     * already expired ({@code expiresAt} at or before now), nothing is stored.
     * Implementations must swallow Redis connectivity/write errors so the caller
     * (e.g. URL creation) is never blocked by a cache problem.
     *
     * @param shortCode the redirect path segment
     * @param value     the value to cache
     */
    void put(String shortCode, CachedUrl value);

    /**
     * Removes the entry for a given short code, if present. Wired into the
     * deletion flow in a later phase; unused for now (no delete endpoint yet).
     */
    void evict(String shortCode);
}