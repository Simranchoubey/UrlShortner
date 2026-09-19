package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.urlshortener.util.ShortCodeGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link UrlService} business rules, using a mocked repository
 * and a deterministic fake generator so results never depend on random codes.
 */
class UrlServiceTest {

    private UrlRepository urlRepository;
    private UrlCache urlCache;
    private ClickEventPublisher clickEventPublisher;
    private AnalyticsService analyticsService;
    private UrlService service;
    // Deterministic render time for redirect/expiry checks — set well in the future
    // relative to any "past" expiry used in tests.
    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private void fakeGenerator(String... codes) {
        ShortCodeGenerator generator = org.mockito.Mockito.mock(ShortCodeGenerator.class);
        org.mockito.BDDMockito.BDDStubber stubbing =
                org.mockito.BDDMockito.willReturn(codes[0]);
        for (int i = 1; i < codes.length; i++) {
            stubbing = stubbing.willReturn(codes[i]);
        }
        stubbing.given(generator).generate();
        urlRepository = org.mockito.Mockito.mock(UrlRepository.class);
        // Cache is mocked so unrelated tests never hit a live/in-memory cache and
        // it defaults to a miss (Optional.empty) → database is the source of truth.
        urlCache = org.mockito.Mockito.mock(UrlCache.class);
        org.mockito.BDDMockito.given(urlCache.get(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(java.util.Optional.empty());
        // UserRepository is mocked; only needed at construction since createUrl tests
        // pass a null userId (no ownership lookup is performed).
        com.example.urlshortener.repository.UserRepository userRepository =
                org.mockito.Mockito.mock(com.example.urlshortener.repository.UserRepository.class);
        // Phase 8 deps: a lenient ClickEventPublisher mock (publishing redirects does
        // nothing by default) and a lenient AnalyticsService mock.
        clickEventPublisher = org.mockito.Mockito.mock(ClickEventPublisher.class);
        analyticsService = org.mockito.Mockito.mock(AnalyticsService.class);
        service = new UrlService(urlRepository, userRepository, generator, urlCache,
                clickEventPublisher, analyticsService, "http://localhost:8080", fixedClock);
    }

    private static CreateUrlRequest request(String url) {
        return new CreateUrlRequest(url, null, null);
    }

    // --- Success -----------------------------------------------------------------

    @Test
    void validUrlCreatesSuccessfullyAndIsPersisted() {
        fakeGenerator("aB72xK");
        when(urlRepository.existsByShortCode("aB72xK")).thenReturn(false);
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUrlResponse response = service.createUrl(request("https://example.com/very/long/path"), null);

        assertThat(response.shortCode()).isEqualTo("aB72xK");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/aB72xK");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/very/long/path");

        verify(urlRepository).saveAndFlush(any(Url.class));
    }

    @Test
    void customAliasIsUsedInTheShortUrl() {
        fakeGenerator("aB72xK");
        when(urlRepository.existsByShortCode("aB72xK")).thenReturn(false);
        when(urlRepository.existsByCustomAlias("my-project")).thenReturn(false);
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUrlResponse response =
                service.createUrl(new CreateUrlRequest("https://example.com/x", "my-project", null), null);

        assertThat(response.customAlias()).isEqualTo("my-project");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/my-project");
    }

    @Test
    void collisionCausesRegeneration() {
        // First generated code collides, second is free.
        fakeGenerator("AAAAAAA", "BBBBBBB");
        when(urlRepository.existsByShortCode("AAAAAAA")).thenReturn(true);
        when(urlRepository.existsByShortCode("BBBBBBB")).thenReturn(false);
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUrlResponse response = service.createUrl(request("https://example.com/x"), null);

        assertThat(response.shortCode()).isEqualTo("BBBBBBB");
    }

    @Test
    void generationExhaustionRaisesServerError() {
        // Every candidate collides until the bounded retry budget is used up.
        fakeGenerator("AAAAAAA");
        when(urlRepository.existsByShortCode(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createUrl(request("https://example.com/x"), null))
            .isInstanceOf(ShortCodeGenerationException.class);
        verify(urlRepository, times(5)).existsByShortCode(any());
    }

    // --- Validation -------------------------------------------------------------

    @Test
    void emptyUrlIsRejected() {
        fakeGenerator("AAAAAAA");
        assertThatThrownBy(() -> service.createUrl(request("   "), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void malformedUrlIsRejected() {
        fakeGenerator("AAAAAAA");
        assertThatThrownBy(() -> service.createUrl(request("not a valid url"), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void unsupportedSchemeIsRejected() {
        fakeGenerator("AAAAAAA");
        assertThatThrownBy(() -> service.createUrl(request("ftp://example.com/file"), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void oversizedUrlIsRejected() {
        fakeGenerator("AAAAAAA");
        String huge = "https://example.com/" + "x".repeat(2100);
        assertThatThrownBy(() -> service.createUrl(request(huge), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void invalidAliasIsRejected() {
        fakeGenerator("AAAAAAA");
        assertThatThrownBy(() -> service.createUrl(new CreateUrlRequest("https://example.com/x", "a!@#", null), null))
            .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.createUrl(new CreateUrlRequest("https://example.com/x", "ab", null), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void expirationInThePastIsRejected() {
        fakeGenerator("AAAAAAA");
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.createUrl(
                new CreateUrlRequest("https://example.com/x", null, past), null))
            .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void duplicateAliasReturns409Conflict() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.existsByCustomAlias("my-project")).thenReturn(true);

        assertThatThrownBy(() -> service.createUrl(
                new CreateUrlRequest("https://example.com/x", "my-project", null), null))
            .isInstanceOf(DuplicateAliasException.class);
    }

    // --- Redirect resolution (Phase 4) ------------------------------------------

    private static Url url(String shortCode, String originalUrl, OffsetDateTime expiresAt) {
        Url url = new Url();
        url.setShortCode(shortCode);
        url.setOriginalUrl(originalUrl);
        url.setExpiresAt(expiresAt);
        return url;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    @Test
    void resolvesNeverExpiringShortCodeToOriginalUrl() {
        fakeGenerator("AAAAAAA");
        Url stored = url("aB72xK", "https://example.com/very/long/path", null);
        when(urlRepository.findByShortCode("aB72xK")).thenReturn(java.util.Optional.of(stored));

        assertThat(service.resolveShortCode("aB72xK"))
                .isEqualTo("https://example.com/very/long/path");
    }

    @Test
    void resolvesFutureExpiryShortCodeToOriginalUrl() {
        fakeGenerator("AAAAAAA");
        Url stored = url("fut1234", "https://example.com/future", utc(Instant.parse("2035-06-01T00:00:00Z")));
        when(urlRepository.findByShortCode("fut1234")).thenReturn(java.util.Optional.of(stored));

        assertThat(service.resolveShortCode("fut1234"))
                .isEqualTo("https://example.com/future");
    }

    @Test
    void rejectsExpiredShortCode() {
        fakeGenerator("AAAAAAA");
        Url stored = url("exp1234", "https://example.com/past", utc(Instant.parse("2020-01-01T00:00:00Z")));
        when(urlRepository.findByShortCode("exp1234")).thenReturn(java.util.Optional.of(stored));

        assertThatThrownBy(() -> service.resolveShortCode("exp1234"))
            .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void expiringAtExactlyNowIsExpired() {
        fakeGenerator("AAAAAAA");
        // expiresAt equals the clock instant → not strictly "after" → expired (410).
        Url stored = url("edge123", "https://example.com/edge", utc(fixedClock.instant()));
        when(urlRepository.findByShortCode("edge123")).thenReturn(java.util.Optional.of(stored));

        assertThatThrownBy(() -> service.resolveShortCode("edge123"))
            .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void unknownShortCodeThrowsNotFound() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByShortCode("nope123")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.resolveShortCode("nope123"))
            .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void preservesQueryFragmentAndPathInDestinationUrl() {
        fakeGenerator("AAAAAAA");
        String destination =
                "https://example.com/deep/path?query=1&other=%20x#section-2";
        Url stored = url("spec123", destination, null);
        when(urlRepository.findByShortCode("spec123")).thenReturn(java.util.Optional.of(stored));

        assertThat(service.resolveShortCode("spec123")).isEqualTo(destination);
    }

    // --- Cache integration with the resolver (Phase 5) --------------------------

    private static com.example.urlshortener.cache.CachedUrl cached(
            String originalUrl, Instant expiresAt) {
        return new com.example.urlshortener.cache.CachedUrl(originalUrl, expiresAt);
    }

    @Test
    void cacheHitReturnsOriginalUrlWithoutQueryingDatabase() {
        fakeGenerator("AAAAAAA");
        // Prime the cache → the resolver must NOT hit the repository.
        when(urlCache.get("hit1234"))
                .thenReturn(java.util.Optional.of(cached("https://example.com/from-cache", null)));

        assertThat(service.resolveShortCode("hit1234"))
                .isEqualTo("https://example.com/from-cache");
        // No DB interaction on a cache hit.
        verify(urlRepository, org.mockito.Mockito.never()).findByShortCode("hit1234");
    }

    @Test
    void cacheMissQueriesDatabaseAndPopulatesCache() {
        fakeGenerator("AAAAAAA");
        Url stored = url("miss123", "https://example.com/from-db", null);
        when(urlRepository.findByShortCode("miss123")).thenReturn(java.util.Optional.of(stored));

        assertThat(service.resolveShortCode("miss123"))
                .isEqualTo("https://example.com/from-db");
        // The valid result is written back into the cache.
        org.mockito.BDDMockito.then(urlCache).should()
                .put(org.mockito.ArgumentMatchers.eq("miss123"),
                        org.mockito.ArgumentMatchers.argThat(c ->
                                c.originalUrl().equals("https://example.com/from-db")
                                        && c.expiresAt() == null));
    }

    @Test
    void expiredCachedEntryIsRejectedEvenIfNotInDatabase() {
        fakeGenerator("AAAAAAA");
        // Cache holds an expired value with no matching DB row → must still be 410.
        when(urlCache.get("cacexp1"))
                .thenReturn(java.util.Optional.of(cached(
                        "https://example.com/old", Instant.parse("2020-01-01T00:00:00Z"))));

        assertThatThrownBy(() -> service.resolveShortCode("cacexp1"))
            .isInstanceOf(UrlExpiredException.class);
        verify(urlRepository, org.mockito.Mockito.never()).findByShortCode("cacexp1");
    }

    @Test
    void neverExpiringCachedEntryRedirects() {
        fakeGenerator("AAAAAAA");
        when(urlCache.get("nevexp1"))
                .thenReturn(java.util.Optional.of(cached("https://example.com/forever", null)));

        assertThat(service.resolveShortCode("nevexp1"))
                .isEqualTo("https://example.com/forever");
        verify(urlRepository, org.mockito.Mockito.never()).findByShortCode("nevexp1");
    }

    @Test
    void createUrlWarmsTheCacheWithPersistedValue() {
        fakeGenerator("warm123");
        when(urlRepository.existsByShortCode("warm123")).thenReturn(false);
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createUrl(request("https://example.com/very/long/path"), null);

        // Cache warmed with the created short code.
        org.mockito.BDDMockito.then(urlCache).should()
                .put(org.mockito.ArgumentMatchers.eq("warm123"),
                        org.mockito.ArgumentMatchers.argThat(c ->
                                c.originalUrl().equals("https://example.com/very/long/path")));
    }

    // --- Phase 7: URL management (list / get / delete / analytics) -------------

    /** Builds a fully-owned URL with an id and a user id for ownership-scoped tests. */
    private static Url ownedUrl(Long id, Long ownerId, String shortCode, String originalUrl) {
        Url url = new Url();
        // Url exposes no setId; the id is set reflectively (it is only read via the
        // getter). ownerId is not referenced by the service logic in these unit
        // tests — ownership is expressed through the mocked findByIdAndUserId.
        org.springframework.test.util.ReflectionTestUtils.setField(url, "id", id);
        com.example.urlshortener.domain.User owner = new com.example.urlshortener.domain.User();
        owner.setEmail("owner@example.com");
        owner.setPassword("hash");
        url.setUser(owner);
        url.setShortCode(shortCode);
        url.setOriginalUrl(originalUrl);
        return url;
    }

    @Test
    void getUrlReturnsOwnedUrlMappedToDto() {
        fakeGenerator("AAAAAAA");
        Url owned = ownedUrl(10L, 1L, "myOwn01", "https://example.com/owner");
        when(urlRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(owned));

        UrlResponse response = service.getUrl(10L, 1L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.shortCode()).isEqualTo("myOwn01");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/myOwn01");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/owner");
    }

    @Test
    void getUrlBelongingToAnotherUserThrowsNotFound() {
        fakeGenerator("AAAAAAA");
        // Row 10 exists and is owned by user 1 — but the caller (user 2) must not
        // see it: findByIdAndUserId(10, 2) is empty, so it is a 404 like any unknown id.
        when(urlRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrl(10L, 2L))
            .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getUnknownUrlThrowsNotFound() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrl(99L, 1L))
            .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void listUrlsPaginatesAndScopesToUser() {
        fakeGenerator("AAAAAAA");
        Url first = ownedUrl(2L, 1L, "newest01", "https://example.com/newest");
        Url second = ownedUrl(1L, 1L, "oldest01", "https://example.com/oldest");
        Page<Url> page = new PageImpl<>(List.of(first, second), Pageable.ofSize(20), 2L);
        when(urlRepository.findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(page);

        UrlListResponse response = service.listUrls(1L, 0, 20);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(UrlResponse::shortCode)
                .containsExactly("newest01", "oldest01");
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.page()).isZero();
    }

    @Test
    void listUrlsClampsPageSizeToMaximum() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(100), 0L));

        service.listUrls(5L, 0, 10_000);

        // The size handed to the repository must never exceed 100 (§3 of the plan).
        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(urlRepository).findByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(5L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void deleteOwnedUrlDeletesFromDatabaseAndEvictsCache() {
        fakeGenerator("AAAAAAA");
        Url owned = ownedUrl(10L, 1L, "delCode1", "https://example.com/x");
        when(urlRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(owned));

        service.deleteUrl(10L, 1L);

        verify(urlRepository).delete(owned);
        // After the database delete, the cache entry for the short code is evicted
        // so a stale redirect is never served again (§6).
        org.mockito.BDDMockito.then(urlCache).should()
                .evict("delCode1");
    }

    @Test
    void deleteAnotherUsersUrlThrowsNotFoundAndDoesNotDelete() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUrl(10L, 2L))
            .isInstanceOf(UrlNotFoundException.class);
        // No deletion, and no cache eviction, for a URL the caller does not own.
        verify(urlRepository, org.mockito.Mockito.never()).delete(any(Url.class));
        org.mockito.BDDMockito.then(urlCache).should(org.mockito.Mockito.never())
                .evict(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deleteUnknownUrlThrowsNotFound() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUrl(99L, 1L))
            .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void deleteSucceedsEvenWhenRedisEvictThrowsWithinAnActiveTransaction() {
        // Only relevant when the service can't rely on the cache's own failure
        // tolerance: within an active transaction the factory no-ops instead of
        // calling redis synchronously. Redis's own failure tolerance while actually
        // evicting is covered in RedisUrlCacheTest.evictSwallowsRedisFailure... —
        // the service simply schedules the eviction after commit and never makes it
        // a prerequisite for the database delete.
        fakeGenerator("AAAAAAA");
        Url owned = ownedUrl(10L, 1L, "delCode3", "https://example.com/y3");
        when(urlRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(owned));

        service.deleteUrl(10L, 1L);

        verify(urlRepository).delete(owned);
    }

    @Test
    void getAnalyticsEnforcesOwnershipAndReturnsRealClickCount() {
        fakeGenerator("AAAAAAA");
        Url owned = ownedUrl(10L, 1L, "anaCode1", "https://example.com/z");
        when(urlRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(owned));
        // Phase 8: the owner sees the persisted click count from the AnalyticsService.
        when(analyticsService.countClicksForUrl(10L)).thenReturn(42L);

        UrlAnalyticsResponse response = service.getAnalytics(10L, 1L);

        assertThat(response.totalClicks()).isEqualTo(42L);
    }

    @Test
    void getAnalyticsReturnsZeroWhenNoClickEventsPersisted() {
        fakeGenerator("AAAAAAA");
        Url owned = ownedUrl(10L, 1L, "anaZero1", "https://example.com/z0");
        when(urlRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(owned));
        when(analyticsService.countClicksForUrl(10L)).thenReturn(0L);

        UrlAnalyticsResponse response = service.getAnalytics(10L, 1L);

        assertThat(response.totalClicks()).isZero();
    }

    @Test
    void getAnalyticsForAnotherUsersUrlThrowsNotFound() {
        fakeGenerator("AAAAAAA");
        when(urlRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalytics(10L, 2L))
            .isInstanceOf(UrlNotFoundException.class);
        // No aggregation is consulted for a foreign URL (IDOR must never leak a count).
        verify(analyticsService, org.mockito.Mockito.never())
                .countClicksForUrl(org.mockito.ArgumentMatchers.anyLong());
    }

    // --- Phase 8: click-event publishing from the redirect -------------------

    @Test
    void databasePathPublishesClickEventWithUrlIdAndMetadata() {
        fakeGenerator("AAAAAAA");
        Url stored = urlWithId(10L, "pubDb000", "https://example.com/db-path");
        when(urlRepository.findByShortCode("pubDb000")).thenReturn(Optional.of(stored));

        service.resolveShortCode("pubDb000", "https://ref.example", "curl/8.0", "203.0.113.4");

        org.mockito.ArgumentCaptor<ClickEventMessage> captor =
                org.mockito.ArgumentCaptor.forClass(ClickEventMessage.class);
        verify(clickEventPublisher).publishClick(captor.capture());
        ClickEventMessage msg = captor.getValue();
        assertThat(msg.urlId()).isEqualTo(10L);
        assertThat(msg.shortCode()).isEqualTo("pubDb000");
        assertThat(msg.referrer()).isEqualTo("https://ref.example");
        assertThat(msg.userAgent()).isEqualTo("curl/8.0");
        assertThat(msg.ip()).isEqualTo("203.0.113.4");
        assertThat(msg.country()).isNull();
        assertThat(msg.eventTime()).isNotNull();
    }

    @Test
    void cacheHitPathPublishesClickEventWithoutUrlId() {
        fakeGenerator("AAAAAAA");
        when(urlCache.get("pubHit01"))
                .thenReturn(Optional.of(new com.example.urlshortener.cache.CachedUrl(
                        "https://example.com/hit", (java.time.Instant) null)));

        service.resolveShortCode("pubHit01", null, null, "192.0.2.9");

        org.mockito.ArgumentCaptor<ClickEventMessage> captor =
                org.mockito.ArgumentCaptor.forClass(ClickEventMessage.class);
        verify(clickEventPublisher).publishClick(captor.capture());
        ClickEventMessage msg = captor.getValue();
        // No DB query on a cache hit → urlId is unknown; the consumer resolves it.
        assertThat(msg.urlId()).isNull();
        assertThat(msg.shortCode()).isEqualTo("pubHit01");
        assertThat(msg.ip()).isEqualTo("192.0.2.9");
    }

    @Test
    void redirectStillSucceedsEvenWhenPublishingThrows() {
        fakeGenerator("AAAAAAA");
        Url stored = urlWithId(10L, "pubFail", "https://example.com/fail");
        when(urlRepository.findByShortCode("pubFail")).thenReturn(Optional.of(stored));
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down"))
                .when(clickEventPublisher).publishClick(org.mockito.ArgumentMatchers.any());

        // The redirect must not fail because analytics publishing failed (§9).
        assertThat(service.resolveShortCode("pubFail"))
                .isEqualTo("https://example.com/fail");
    }

    private static Url urlWithId(Long id, String shortCode, String originalUrl) {
        Url url = new Url();
        org.springframework.test.util.ReflectionTestUtils.setField(url, "id", id);
        url.setShortCode(shortCode);
        url.setOriginalUrl(originalUrl);
        return url;
    }
}