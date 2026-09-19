package com.example.urlshortener.service;

import com.example.urlshortener.cache.CachedUrl;
import com.example.urlshortener.cache.UrlCache;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.CreateUrlResponse;
import com.example.urlshortener.dto.UrlAnalyticsResponse;
import com.example.urlshortener.dto.UrlListResponse;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.exception.DuplicateAliasException;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortCodeGenerationException;
import com.example.urlshortener.exception.UrlExpiredException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.messaging.ClickEventMessage;
import com.example.urlshortener.messaging.ClickEventPublisher;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.util.ShortCodeGenerator;
import com.example.urlshortener.util.UrlValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Single URL creation: validate the request, generate a unique random Base62
 * short code (bounded collision retry), reject duplicate custom aliases, enforce
 * expiration validation, and persist the row.
 *
 * <p>Flow: Controller → UrlService → validation → ShortCodeGenerator →
 * UrlRepository → response DTO (§8 of the plan).
 */
@Service
public class UrlService {

    /** How many times we attempt to draw a fresh, unused short code. */
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    /** Default page size for {@code GET /api/v1/urls} when the caller omits it. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Hard upper bound on page size (§1 of the plan) — larger values are clamped. */
    private static final int MAX_PAGE_SIZE = 100;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlCache urlCache;
    private final ClickEventPublisher clickEventPublisher;
    private final AnalyticsService analyticsService;
    private final String baseUrl;
    private final Clock clock;

    public UrlService(
            UrlRepository urlRepository,
            UserRepository userRepository,
            ShortCodeGenerator shortCodeGenerator,
            UrlCache urlCache,
            ClickEventPublisher clickEventPublisher,
            AnalyticsService analyticsService,
            @Value("${app.base-url}") String baseUrl,
            Clock clock) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlCache = urlCache;
        this.clickEventPublisher = clickEventPublisher;
        this.analyticsService = analyticsService;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.clock = clock;
    }

    /**
     * Creates and persists a new short URL.
     *
     * @param request the validated request payload
     * @param userId  authenticated user's id (from the JWT filter) — associated as
     *                the URL's owner; may be null for a defensive anonymous row
     * @throws InvalidUrlException if URL/alias/expiration are invalid
     * @throws DuplicateAliasException if the custom alias is already taken
     * @throws ShortCodeGenerationException if no free code could be found
     */
    @Transactional
    public CreateUrlResponse createUrl(CreateUrlRequest request, Long userId) {
        UrlValidator.validateOriginalUrl(request.originalUrl());

        Instant expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidUrlException("expiresAt must be in the future");
        }

        String customAlias = normaliseCustomAlias(request.customAlias());

        String pathKey;
        if (customAlias != null) {
            // A caller-supplied alias becomes the redirect key: the short URL path
            // is the alias ("https://short.ly/my-project"). Uniqueness is enforced
            // by the DB index on custom_alias; because the alias is also stored as
            // short_code, it must be free in both columns.
            if (urlRepository.existsByCustomAlias(customAlias)
                    || urlRepository.existsByShortCode(customAlias)) {
                throw new DuplicateAliasException("custom alias is already in use: " + customAlias);
            }
            pathKey = customAlias;
        } else {
            pathKey = reserveShortCode();
        }

        Url url = new Url();
        url.setShortCode(pathKey);
        url.setOriginalUrl(request.originalUrl().trim());
        url.setCustomAlias(customAlias);
        url.setExpiresAt(toOffsetDateTime(expiresAt));
        // Phase 6: associate the URL with the authenticated user. userId comes from
        // the JWT (the controller never trusts a client-supplied id). When present we
        // attach a lazy reference to the owning User; the column stays nullable so a
        // null id (defensive fallback) still yields an anonymous row.
        if (userId != null) {
            url.setUser(userRepository.getReferenceById(userId));
        }

        try {
            urlRepository.saveAndFlush(url);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request claimed the alias/code between our pre-check and
            // the insert; the DB unique indexes remain the final guards.
            throw new DuplicateAliasException("a resource with that identifier already exists");
        }

        // Cache warming (§5): populate Redis so the first redirect is served from
        // cache. This is best-effort — Redis failures are swallowed inside the
        // cache implementation, so URL creation still succeeds (201) purely from
        // PostgreSQL being the source of truth.
        urlCache.put(pathKey, CachedUrl.from(url));

        return toResponse(url);
    }

    /**
     * Resolves a short code (or custom alias, which is stored as the same
     * {@code short_code} value) to its original URL for the public redirect
     * endpoint, applying the redirect-time expiration rule.
     *
     * <p>Cache-aside flow (§11):
     * <pre>
     * 1. Try Redis (cache hit → verify expiry → return original URL).
     * 2. Cache miss → query PostgreSQL.
     * 3. Not found        → UrlNotFoundException (404).
     * 4. Expired          → UrlExpiredException (410).
     * 5. Valid            → store in Redis, return original URL.
     * </pre>
     *
     * <p>The cache never overrides the source of truth: an entry is still checked
     * against {@code expiresAt} (Redis TTL alone is not trusted, §7), and any
     * Redis read failure degrades to a database lookup. The database remains the
     * authority for existence and expiry.
     *
     * <p>Expired URLs are left in the database per §4 (no deletion or background
     * cleanup in this phase). The returned value is only used to set the
     * {@code Location} header — the server never fetches the destination (no
     * SSRF risk), because the URL was already validated as http/https on creation.
     *
     * @param shortCode the path segment of the short link (never used to build SQL
     *                  — Spring Data/JPA parameter binding prevents injection)
     * @return the original long URL to redirect the client to
     * @throws UrlNotFoundException if no such short code exists
     * @throws UrlExpiredException if the link has expired
     * @see #resolveShortCode(String, String, String, String) which additionally
     *      captures redirect request metadata for click-event publishing (Phase 8)
     */
    @Transactional(readOnly = true)
    public String resolveShortCode(String shortCode) {
        return resolveShortCode(shortCode, null, null, null);
    }

    /**
     * Resolves a short code for a redirect, returning the original URL and, as a
     * side effect, publishing an asynchronous click event (Phase 8).
     *
     * <p>This is the same cache-aside behaviour as the single-argument overload but
     * also records the request metadata available on the HTTP redirect request. The
     * publish is strictly best-effort and fire-and-forget: a Kafka/data failure is
     * never allowed to delay or fail the 302 (IMPLEMENTATION_PLAN.md §9 / §19). The
     * {@code urlId} carried on the message is the URL's database id on the cache-miss
     * path, and {@code null} on a cache hit — the consumer resolves it from the short
     * code so no extra database query is issued on the redirect hot path.
     *
     * @param shortCode the path segment of the short link
     * @param referrer  the HTTP {@code Referer} header, or {@code null}
     * @param userAgent the HTTP {@code User-Agent} header, or {@code null}
     * @param ip        the client IP, or {@code null}
     * @return the original long URL to redirect the client to
     * @throws UrlNotFoundException if no such short code exists
     * @throws UrlExpiredException if the link has expired
     */
    @Transactional(readOnly = true)
    public String resolveShortCode(String shortCode, String referrer, String userAgent, String ip) {
        // 1. Try Redis first — on a hit we must NOT query PostgreSQL (§4).
        Optional<CachedUrl> cached = urlCache.get(shortCode);
        if (cached.isPresent()) {
            CachedUrl value = cached.get();
            // 2. Respect expiresAt even when cached (§7): Redis TTL alone is not enough.
            if (value.expiresAt() != null && !value.expiresAt().isAfter(clock.instant())) {
                throw new UrlExpiredException("short code has expired: " + shortCode);
            }
            publishClick(null, shortCode, referrer, userAgent, ip);
            return value.originalUrl();
        }

        // 3. Cache miss → authoritative lookup from PostgreSQL.
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("short code not found: " + shortCode));

        if (url.getExpiresAt() != null && !url.getExpiresAt().toInstant().isAfter(clock.instant())) {
            throw new UrlExpiredException("short code has expired: " + shortCode);
        }

        // 6. Populate the cache so future redirects hit (best-effort).
        urlCache.put(shortCode, CachedUrl.from(url));

        publishClick(url.getId(), shortCode, referrer, userAgent, ip);
        return url.getOriginalUrl();
    }

    /**
     * Draws a random Base62 code, skipping any already used, up to a bounded
     * number of attempts. The DB stays the source of truth for final uniqueness;
     * this pre-check cheaply avoids most collisions.
     */
    private String reserveShortCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!urlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new ShortCodeGenerationException(
                "could not generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String normaliseCustomAlias(String customAlias) {
        // Throws InvalidUrlException on bad input; returns false only when absent.
        if (UrlValidator.validateCustomAlias(customAlias)) {
            return customAlias.trim();
        }
        return null;
    }

    // --- Phase 7: URL management (list / get / delete / analytics) -----------

    /**
     * Lists the authenticated user's own URLs, newest first, with Spring Data
     * pagination. Ownership is enforced by the repository query itself
     * ({@code findByUserIdOrderByCreatedAtDesc}), so the caller's rows are the
     * only ones ever considered.
     *
     * @param userId authenticated user's id (from the JWT) — never client-supplied
     * @param page   zero-based page index (null → 0)
     * @param size   page size (null → default 20; values &gt; 100 are clamped to 100)
     */
    @Transactional(readOnly = true)
    public UrlListResponse listUrls(Long userId, Integer page, Integer size) {
        PageRequest pageable = pageRequest(userId, page, size);
        Page<Url> result = urlRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new UrlListResponse(
                result.getContent().stream()
                        .map(u -> UrlResponse.from(u, baseUrl))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }

    /**
     * Returns one of the authenticated user's URLs. Uses the ownership-aware
     * lookup {@code findByIdAndUserId} so a URL owned by another user (or an
     * unknown id) is indistinguishable — both yield {@code 404} (§9).
     *
     * @throws UrlNotFoundException if the id is unknown OR belongs to another user
     */
    @Transactional(readOnly = true)
    public UrlResponse getUrl(Long id, Long userId) {
        Url url = findOwned(id, userId);
        return UrlResponse.from(url, baseUrl);
    }

    /**
     * Deletes one of the authenticated user's URLs. Ownership is enforced before
     * deletion via {@code findByIdAndUserId} — a URL owned by another user (or an
     * unknown id) is a {@code 404}, never a {@code 403} or {@code 204}.
     *
     * <p>Ordering (§6/§7 of the plan): PostgreSQL delete commits first, then the
     * {@code url:{shortCode}} entry is evicted from Redis via a
     * post-commit synchronization. A Redis eviction failure cannot roll back the
     * already-committed database delete (and the Redis cache implementation
     * already swallows connectivity errors).
     *
     * @throws UrlNotFoundException if the id is unknown OR belongs to another user
     */
    @Transactional
    public void deleteUrl(Long id, Long userId) {
        Url url = findOwned(id, userId);
        urlRepository.delete(url);

        // Evict only after the database delete commits, so the short code never
        // serves a stale cached redirect again (and so the cache is consistent
        // with PostgreSQL, the source of truth). Redis failures are best-effort.
        String shortCode = url.getShortCode();
        registerEvictAfterCommit(shortCode);
    }

    /**
     * Returns the analytics response for one of the authenticated user's URLs —
     * the real persisted click count (Phase 8). Ownership is enforced the same way
     * as {@link #getUrl}; another user's URL is a {@code 404}. The count reflects
     * whatever has been asynchronously persisted by the Kafka consumer (PostgreSQL
     * is the source of truth), so it is {@code 0} before any event has been
     * recorded.
     *
     * @throws UrlNotFoundException if the id is unknown OR belongs to another user
     */
    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(Long id, Long userId) {
        // Ownership/IDOR enforcement (Phase 7 §8): a foreign or unknown id is a 404.
        Url url = findOwned(id, userId);
        // Phase 8: return the real persisted click count (asynchronously written by
        // the Kafka consumer). Aggregation is read-only and never blocks the redirect.
        long totalClicks = analyticsService.countClicksForUrl(url.getId());
        return new UrlAnalyticsResponse(totalClicks);
    }

    /**
     * Looks up a URL only if it is owned by the given user. Both a nonexistent id
     * and another user's id map to the same {@link UrlNotFoundException} so the
     * response never reveals whether the resource exists (§9 — avoid enumeration).
     */
    private Url findOwned(Long id, Long userId) {
        return urlRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("url not found"));
    }

    /**
     * Builds a {@link PageRequest} ordered newest-first. Validates/normalises the
     * requested page and size (§3): page is non-negative, size defaults to 20 and
     * is clamped to at most 100.
     */
    private PageRequest pageRequest(Long userId, Integer page, Integer size) {
        int resolvedPage = (page == null || page < 0) ? 0 : page;
        int resolvedSize = normalizeSize(size);
        return PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Publishes a click event for a successful redirect (fire-and-forget, best-effort).
     *
     * <p>Formatting the message and handing it to {@link ClickEventPublisher} is all
     * that happens here — the publisher implementations swallow transport failures, so
     * a Kafka outage can only make analytics lag, never alter or delay the 302
     * (IMPLEMENTATION_PLAN.md §9 / §19). Missing metadata ({@code referrer},
     * {@code userAgent}, {@code ip}) is passed through as {@code null} rather than
     * failing the redirect (§5).
     */
    private void publishClick(Long urlId, String shortCode, String referrer, String userAgent, String ip) {
        ClickEventMessage message = new ClickEventMessage(
                urlId,
                shortCode,
                OffsetDateTime.now(clock),
                referrer,
                userAgent,
                ip,
                null); // country: no geolocation in this phase (§5)
        try {
            clickEventPublisher.publishClick(message);
        } catch (RuntimeException ex) {
            // Defense-in-depth (§9): even if a publisher implementation misbehaves,
            // analytics must never block or fail the redirect. Log and continue.
            log.warn("Click event publish failed for shortCode '{}'; redirect continues: {}",
                    shortCode, ex.getMessage());
        }
    }

    /**
     * Schedules {@link UrlCache#evict} to run after the current transaction
     * commits. If no transaction is active, evicts immediately. Redis is never a
     * prerequisite for a successful delete — the eviction is best-effort.
     */
    private void registerEvictAfterCommit(String shortCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            urlCache.evict(shortCode);
                        }
                    });
        } else {
            urlCache.evict(shortCode);
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        // Store timestamps in UTC, matching hibernate.jdbc.time_zone=UTC.
        return (instant == null) ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private CreateUrlResponse toResponse(Url url) {
        return new CreateUrlResponse(
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getCustomAlias(),
                toInstant(url.getExpiresAt()));
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return (offsetDateTime == null) ? null : offsetDateTime.toInstant();
    }

    private static String trimTrailingSlash(String baseUrl) {
        return (baseUrl != null && baseUrl.endsWith("/"))
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}