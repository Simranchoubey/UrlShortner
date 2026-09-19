package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.cache.InMemoryUrlCache;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.domain.User;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer integration tests for Phase 7 URL management: list (paginated),
 * single get, delete (with Redis eviction), and the analytics API boundary, plus
 * the ownership/IDOR guarantees.
 *
 * <p>Like the other suites, these run under the {@code test} profile against the
 * in-memory H2 database and the {@link InMemoryUrlCache} (Docker/PostgreSQL/Redis
 * are not available on this machine). The authenticated identity always comes
 * from the JWT; the tests verify user B can never see/delete user A's URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlManagementIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InMemoryUrlCache cache;

    /** Global counter so each test/fixture uses unique emails + short codes across the shared context. */
    private static int sequence = 0;

    /** Registers a fresh user; returns the new account's id. */
    private long register(String email) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();
    }

    /** Logs in an already-registered user by email; returns the bearer token. */
    private String login(String email) throws Exception {
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Registers a fresh user and returns their bearer token. */
    private String bearerToken() throws Exception {
        String email = "mgmt" + (++sequence) + "@example.com";
        register(email);
        return login(email);
    }

    /** Creates a URL for the authenticated caller; returns the response JSON. */
    private JsonNode createUrl(String bearer, String originalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"" + originalUrl + "\", \"customAlias\": null, \"expiresAt\": null }"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Returns the persisted {@link Url} for a short code (to read its database id). */
    private Url persistedUrl(String shortCode) {
        return urlRepository.findByShortCode(shortCode).orElseThrow();
    }

    private static String uniqueShortCode(String prefix) {
        return prefix + (++sequence);
    }

    /** Pauses long enough to ensure two {@link java.time.OffsetDateTime#now()} calls differ. */
    private static void sleepBriefly() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while pacing test fixtures", ex);
        }
    }

    // --- List ------------------------------------------------------------------

    @Test
    void listReturnsOnlyTheAuthenticatedUsersOwnUrls() throws Exception {
        String userA = bearerToken();
        String userB = bearerToken();
        JsonNode aCreated = createUrl(userA, "https://example.com/list-a");
        String aCode = aCreated.get("shortCode").asText();
        createUrl(userB, "https://example.com/list-b");

        MvcResult result = mockMvc.perform(get("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");

        // User B's list must contain only B's URLs, never the short code of A's URL.
        boolean containsA = false;
        for (JsonNode item : items) {
            if (aCode.equals(item.get("shortCode").asText())) {
                containsA = true;
            }
        }
        assertThat(containsA).as("user B must not see user A's short code").isFalse();
    }

    @Test
    void listMultipleUrlsReturnsNewestFirst() throws Exception {
        // Deterministic ordering: persist URLs sequentially with a small pause so
        // the auto-assigned createdAt timestamps (OffsetDateTime.now() with ns
        // precision) are guaranteed distinct. All belong to the same authenticated
        // user (the ordering guarantee is user-scoped).
        String email = "mgmt" + (++sequence) + "@example.com";
        long userId = register(email);
        String token = login(email);
        User owner = userRepository.findById(userId).orElseThrow();

        Url oldUrl = new Url();
        oldUrl.setUser(owner);
        oldUrl.setShortCode(uniqueShortCode("ordOld"));
        oldUrl.setOriginalUrl("https://example.com/oldest");
        urlRepository.save(oldUrl);
        sleepBriefly();

        Url newUrl = new Url();
        newUrl.setUser(owner);
        newUrl.setShortCode(uniqueShortCode("ordNew"));
        newUrl.setOriginalUrl("https://example.com/newest");
        urlRepository.save(newUrl);

        MvcResult result = mockMvc.perform(get("/api/v1/urls?page=0&size=100")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");

        // findAll ordered by createdAt DESC → the most recent URL comes first.
        assertThat(items.get(0).get("originalUrl").asText())
                .as("newest URL should appear first")
                .isEqualTo("https://example.com/newest");
        assertThat(items.get(1).get("originalUrl").asText()).isEqualTo("https://example.com/oldest");
    }

    @Test
    void listReturnsPaginationMetadata() throws Exception {
        String token = bearerToken();
        MvcResult result = mockMvc.perform(get("/api/v1/urls?page=0&size=20")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("items")).isTrue();
        assertThat(json.has("page")).isTrue();
        assertThat(json.has("size")).isTrue();
        assertThat(json.has("totalElements")).isTrue();
        assertThat(json.has("totalPages")).isTrue();
    }

    @Test
    void listEnforcesMaximumPageSizeOf100() throws Exception {
        String token = bearerToken();
        // Request a huge page size — the service clamps it to 100 (§3 of the plan)
        // and still returns 200 rather than failing.
        mockMvc.perform(get("/api/v1/urls?page=0&size=10000")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/urls"))
                .andExpect(status().isUnauthorized());
    }

    // --- Get -------------------------------------------------------------------

    @Test
    void ownerCanGetOwnUrl() throws Exception {
        String token = bearerToken();
        JsonNode created = createUrl(token, "https://example.com/own-get");
        String code = created.get("shortCode").asText();
        Long id = persistedUrl(code).getId();

        mockMvc.perform(get("/api/v1/urls/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/own-get"));
    }

    @Test
    void getUnknownUrlReturns404() throws Exception {
        String token = bearerToken();
        mockMvc.perform(get("/api/v1/urls/999999")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAnotherUsersUrlReturns404() throws Exception {
        String tokenA = bearerToken();
        String tokenB = bearerToken();
        JsonNode created = createUrl(tokenA, "https://example.com/a-secret");
        Long idA = persistedUrl(created.get("shortCode").asText()).getId();

        // User B must not be able to read user A's URL → 404 (never 200/403).
        mockMvc.perform(get("/api/v1/urls/" + idA)
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/urls/1"))
                .andExpect(status().isUnauthorized());
    }

    // --- Delete ----------------------------------------------------------------

    @Test
    void ownerCanDeleteUrl() throws Exception {
        String token = bearerToken();
        JsonNode created = createUrl(token, "https://example.com/to-delete");
        String code = created.get("shortCode").asText();
        Long id = persistedUrl(code).getId();

        mockMvc.perform(delete("/api/v1/urls/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        assertThat(urlRepository.findById(id)).isEmpty();
    }

    @Test
    void deleteEvictsRedisCacheEntry() throws Exception {
        String token = bearerToken();
        JsonNode created = createUrl(token, "https://example.com/evict-me");
        String code = created.get("shortCode").asText();
        Long id = persistedUrl(code).getId();

        // The creation warming step populates the cache for this short code.
        assertThat(cache.contains(code)).isTrue();

        mockMvc.perform(delete("/api/v1/urls/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        // After a successful delete the cache entry is evicted so a stale redirect
        // can never be served again (§6 of the plan).
        assertThat(cache.contains(code)).isFalse();
    }

    @Test
    void deleteUnknownUrlReturns404() throws Exception {
        String token = bearerToken();
        mockMvc.perform(delete("/api/v1/urls/999999")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAnotherUsersUrlReturns404() throws Exception {
        String tokenA = bearerToken();
        String tokenB = bearerToken();
        JsonNode created = createUrl(tokenA, "https://example.com/a-to-delete");
        Long idA = persistedUrl(created.get("shortCode").asText()).getId();

        mockMvc.perform(delete("/api/v1/urls/" + idA)
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound());

        // User A's URL must still exist — user B could not delete it.
        assertThat(urlRepository.findById(idA)).isPresent();
    }

    @Test
    void deleteExpiredUrlStillWorksForOwner() throws Exception {
        // Even an expired link remains deletable by its owner (delete is not
        // gated on expiry). Insert the URL directly so we control its expired state.
        String email = "mgmt" + (++sequence) + "@example.com";
        long userId = register(email);
        String token = login(email);
        User owner = userRepository.findById(userId).orElseThrow();

        Url url = new Url();
        url.setUser(owner);
        url.setShortCode(uniqueShortCode("expDel"));
        url.setOriginalUrl("https://example.com/expired");
        url.setExpiresAt(OffsetDateTime.now().minusMinutes(10));
        urlRepository.save(url);
        Long id = url.getId();

        mockMvc.perform(delete("/api/v1/urls/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(urlRepository.findById(id)).isEmpty();
    }

    // --- Analytics -------------------------------------------------------------

    @Test
    void ownerCanAccessAnalytics() throws Exception {
        String token = bearerToken();
        JsonNode created = createUrl(token, "https://example.com/analytics-own");
        Long id = persistedUrl(created.get("shortCode").asText()).getId();

        mockMvc.perform(get("/api/v1/urls/" + id + "/analytics")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                // Phase 7 boundary: totalClicks is always 0 before click aggregation.
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    void analyticsForAnotherUsersUrlReturns404() throws Exception {
        String tokenA = bearerToken();
        String tokenB = bearerToken();
        JsonNode created = createUrl(tokenA, "https://example.com/analytics-hidden");
        Long idA = persistedUrl(created.get("shortCode").asText()).getId();

        mockMvc.perform(get("/api/v1/urls/" + idA + "/analytics")
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsForUnknownUrlReturns404() throws Exception {
        String token = bearerToken();
        mockMvc.perform(get("/api/v1/urls/999999/analytics")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/urls/1/analytics"))
                .andExpect(status().isUnauthorized());
    }
}