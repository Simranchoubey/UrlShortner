package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.ratelimit.InMemoryRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;

/**
 * Web-layer integration tests for Phase 9 rate limiting.
 *
 * <p>These run under the {@code test} profile and <strong>opt in</strong> to rate
 * limiting via {@code @TestPropertySource} with small per-window limits, so the
 * {@code 429} behaviour can be asserted with a handful of requests. The active
 * {@code RateLimiter} is the in-memory double (no real Redis — Docker/Redis is not
 * available on this machine), and the counters are reset between methods so each
 * scenario is deterministic.
 *
 * <p>Because these tests enable a rate-limit property the default test profile has
 * off, they run under their own cached Spring context and do not disturb the other
 * regression suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.window=PT60S",
        "app.rate-limit.create-url-limit=3",
        "app.rate-limit.login-limit=3",
        "app.rate-limit.register-limit=3",
        "app.rate-limit.redirect-enabled=false" // redirect stays fast; asserted below
})
class RateLimitIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryRateLimiter limiter;

    /** Global counter so each scenario uses fresh emails + short codes. */
    private static int sequence = 0;

    @BeforeEach
    void resetRateLimitCounters() {
        limiter.reset();
    }

    @Test
    void registerExceedingIpLimitReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("reg-limit-" + (++sequence) + "@example.com")))
                    .andExpect(status().isCreated());
        }
        // 4th registration from the same client IP → 429.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("reg-limit-" + (++sequence) + "@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                // No internal implementation details may leak to the client.
                .andExpect(jsonPath("$.message", not(containsStringIgnoringCase("redis"))))
                .andExpect(jsonPath("$.message", not(containsStringIgnoringCase("exception"))));
    }

    @Test
    void loginExceedingIpLimitReturns429() throws Exception {
        String email = "login-limit-" + (++sequence) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email)))
                    .andExpect(status().isOk());
        }
        // 4th login from the same client IP → 429.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void createUrlExceedingUserLimitReturns429() throws Exception {
        String bearer = bearerToken();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/urls")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUrlBody("https://exceed-" + (++sequence) + ".com")))
                    .andExpect(status().isCreated());
        }
        // 4th creation by the same authenticated user → 429.
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUrlBody("https://exceed-" + (++sequence) + ".com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void createUrlLimitIsIndependentPerUser() throws Exception {
        String userA = bearerToken();
        String userB = bearerToken();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/urls")
                            .header(HttpHeaders.AUTHORIZATION, userA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUrlBody("https://a-" + (++sequence) + ".com")))
                    .andExpect(status().isCreated());
        }
        // user A is at his limit → 429 on a further creation…
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUrlBody("https://a-over-" + (++sequence) + ".com")))
                .andExpect(status().isTooManyRequests());

        // …but user B has his own independent limit → still allowed.
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, userB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUrlBody("https://b-" + (++sequence) + ".com")))
                .andExpect(status().isCreated());
    }

    @Test
    void redirectStays302EvenWhenRateLimitingEnabled() throws Exception {
        String bearer = bearerToken();
        String shortCode = createUrl(bearer, "https://redirect-target-" + (++sequence) + ".com");

        // Repeated redirects are NOT rate-limited (redirect-enabled=false by
        // default), so the performance-critical path keeps returning 302 + Location.
        for (int i = 0; i < 5; i++) {
            MvcResult result = mockMvc.perform(get("/" + shortCode))
                    .andExpect(status().isFound())
                    .andReturn();
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .startsWith("https://redirect-target-");
        }
    }

    @Test
    void healthProbeIsNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    // --- fixtures -------------------------------------------------------------

    private String registerBody(String email) {
        return "{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }";
    }

    private String loginBody(String email) {
        return "{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }";
    }

    private String createUrlBody(String originalUrl) {
        return "{ \"originalUrl\": \"" + originalUrl + "\", \"customAlias\": null, \"expiresAt\": null }";
    }

    /** Registers + logs in a fresh user; returns their bearer token. */
    private String bearerToken() throws Exception {
        String email = "rl-" + (++sequence) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Creates a URL for the authenticated caller and returns its short code. */
    private String createUrl(String bearer, String originalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUrlBody(originalUrl)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();
    }
}