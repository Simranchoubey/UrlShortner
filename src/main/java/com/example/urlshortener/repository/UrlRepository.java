package com.example.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.urlshortener.domain.Url;

/**
 * Data access for {@link Url}s.
 *
 * <p>{@code findByShortCode} backs the redirect lookup (a cache miss falls back
 * to the indexed {@code short_code} column). The user-scoped queries
 * ({@code findByUserIdOrderByCreatedAtDesc}, {@code findByIdAndUserId}) back the
 * Phase 7 management APIs and enforce ownership directly at the query level so a
 * caller can never retrieve another user's row.
 */
public interface UrlRepository extends JpaRepository<Url, Long> {

    /**
     * Finds the URL for a given short code — the redirect lookup path.
     */
    Optional<Url> findByShortCode(String shortCode);

    /**
     * Fast pre-check for short-code collisions before insert. The database's
     * unique index on {@code short_code} remains the final authority.
     */
    boolean existsByShortCode(String shortCode);

    /**
     * Determines whether a custom alias is already taken, so the service can
     * return {@code 409 Conflict}. The database's unique index on
     * {@code custom_alias} remains the final authority.
     */
    boolean existsByCustomAlias(String customAlias);

    /**
     * Lists a user's own URLs (ordered newest-first) using Spring Data
     * pagination — the rows are queried on a single page, never all at once.
     */
    Page<Url> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Finds a URL only if it belongs to the given user. Returns empty for both a
     * nonexistent id and a URL owned by a different user, so the service layer
     * can map either case to {@code 404} without ever exposing another user's
     * resource (§9 of the plan — avoid enumeration).
     */
    Optional<Url> findByIdAndUserId(Long id, Long userId);
}