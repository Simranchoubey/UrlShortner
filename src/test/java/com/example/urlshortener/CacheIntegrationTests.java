package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.cache.CachedUrl;
import com.example.urlshortener.cache.InMemoryUrlCache;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.repository.UrlRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer integration tests for the Phase 5 cache-aside flow.
 *
 * <p>No real Redis is available on this machine (Docker is unavailable), so these
 * tests run under the {@code test} profile which substitutes the in-memory
 * {@link InMemoryUrlCache}. They exercise the full HTTP → controller → service →
 * cache/repository path and verify cache hit/miss/warm/expiry/TTL behaviour.
 * Each test uses a distinct short code because the Spring context (and its
 * in-memory cache / H2 database) is shared across {@code @SpringBootTest} classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CacheIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private InMemoryUrlCache cache;

    private static int authSequence = 0;

    /**
     * Registers + logs in a fresh user (Phase 6: URL creation requires a JWT)
     * and returns the bearer token to attach to authenticated requests.
     */
    private String bearerToken() throws Exception {
        String email = "cacheit" + (++authSequence) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isCreated());
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isOk())
                .andReturn();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
        return "Bearer " + token;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Url save(String shortCode, String originalUrl, OffsetDateTime expiresAt) {
        Url url = new Url();
        url.setShortCode(shortCode);
        url.setOriginalUrl(originalUrl);
        url.setExpiresAt(expiresAt);
        return urlRepository.saveAndFlush(url);
    }

    // --- Cache hit ---------------------------------------------------------------------

    @Test
    void cacheHitIsServedWithoutQueryingTheDatabase() throws Exception {
        // Prime the cache with a code that has NO database row. If the service
        // consulted PostgreSQL it would 404; a 302 proves the cache alone served it.
        cache.put("cihit01", new CachedUrl("https://example.com/from-cache", null));

        mockMvc.perform(get("/cihit01"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/from-cache"));
    }

    @Test
    void neverExpiringCachedEntryRedirects() throws Exception {
        cache.put("cinex01", new CachedUrl("https://example.com/never-expires", null));

        mockMvc.perform(get("/cinex01"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/never-expires"));
    }

    @Test
    void expiredCachedEntryReturns410() throws Exception {
        // Seed a stale expired entry (as if it were still sitting in Redis with an
        // unconsumed TTL) with no DB row → must be 410, not redirected (§7).
        cache.seed("ciexp01", new CachedUrl("https://example.com/old", Instant.parse("2020-01-01T00:00:00Z")));

        mockMvc.perform(get("/ciexp01"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    // --- Cache miss / population ---------------------------------------------------------

    @Test
    void cacheMissFallsBackToDatabaseAndPopulatesTheCache() throws Exception {
        save("cimis01", "https://example.com/from-db", null);

        mockMvc.perform(get("/cimis01"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/from-db"));

        // After a miss the valid result is stored for the next request.
        assertThat(cache.contains("cimis01")).isTrue();
        assertThat(cache.get("cimis01")).hasValueSatisfying(c ->
                c.originalUrl().equals("https://example.com/from-db"));
    }

    // --- Cache warming on creation -------------------------------------------------------

    @Test
    void creatingAUrlWarmsTheCache() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/warm-me\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andReturn();

        String shortCode = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();

        assertThat(cache.contains(shortCode)).isTrue();
        assertThat(cache.get(shortCode)).hasValueSatisfying(c ->
                c.originalUrl().equals("https://example.com/warm-me"));
    }

    @Test
    void creatingUrlWithAliasWarmsTheCacheUnderAliasKey() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/aliased\","
                                + "\"customAlias\":\"ci-alias-55\"}"))
                .andExpect(status().isCreated());

        assertThat(cache.contains("ci-alias-55")).isTrue();
    }

    // --- TTL -----------------------------------------------------------------------------

    @Test
    void neverExpiringEntryGetsTheDefaultMaximumTtl() throws Exception {
        save("cittl01", "https://example.com/ttl", null);

        mockMvc.perform(get("/cittl01")).andExpect(status().isFound());

        // Default maximum TTL (7 days) applies to never-expiring links.
        assertThat(cache.storedTtl("cittl01")).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void expiringEntryGetsTtlWithinTheMaximum() throws Exception {
        // Expiry ~1 day in the future → shorter than the 7-day maximum.
        Instant expires = Instant.now().plusSeconds(86_400L);
        save("cittl02", "https://example.com/ttl2", utc(expires));

        mockMvc.perform(get("/cittl02")).andExpect(status().isFound());

        Duration stored = cache.storedTtl("cittl02");
        assertThat(stored).isNotNull();
        // Within ~1 day and never larger than the maximum.
        assertThat(stored).isLessThanOrEqualTo(Duration.ofDays(7));
        assertThat(stored).isPositive();
        assertThat(Math.abs(stored.toMillis() - 86_400_000L)).isLessThan(5_000L);
    }
}