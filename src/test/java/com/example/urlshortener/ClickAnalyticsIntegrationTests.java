package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.messaging.ClickEventMessage;
import com.example.urlshortener.messaging.FakeClickEventPublisher;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * Web-layer integration tests for Phase 8 (Kafka + Analytics).
 *
 * <p>Runs under the {@code test} profile against the in-memory H2 database and the
 * {@link FakeClickEventPublisher} (no Kafka/Testcontainers available, per
 * IMPLEMENTATION_PLAN.md test-infrastructure constraint). The redirect flow is
 * exercised end-to-end: it must still return 302 + Location, must publish a click
 * event to the (fake) publisher, and must keep redirecting even when publishing
 * fails. The analytics endpoint returns the real persisted click count only to the
 * URL's owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClickAnalyticsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private FakeClickEventPublisher publisher;

    private static int sequence = 0;

    @BeforeEach
    void resetPublisher() {
        publisher.clear();
    }

    private String bearerToken() throws Exception {
        String email = "analytics" + (++sequence) + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(registered.getResponse().getContentAsString()).get("id").asLong();
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(auth.getResponse().getContentAsString()).get("accessToken").asText();
        return "Bearer " + token;
    }

    private JsonNode createUrl(String bearer, String originalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"" + originalUrl + "\",\"customAlias\":null,\"expiresAt\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Url persistedUrl(String shortCode) {
        return urlRepository.findByShortCode(shortCode).orElseThrow();
    }

    private void persistClick(Url url, String value) {
        ClickEvent click = new ClickEvent();
        click.setUrl(url);
        click.setShortCode(url.getShortCode());
        click.setReferrer("https://referrer.example/" + value);
        click.setUserAgent("agent-" + value);
        click.setIp("198.51.100." + (sequence % 250));
        click.setEventTime(java.time.OffsetDateTime.now());
        clickEventRepository.save(click);
    }

    @Test
    void redirectReturns302WithLocationAndPublishesClickEvent() throws Exception {
        String owner = bearerToken();
        String destination = "https://example.com/phase8-redirect?q=1#top";
        String code = createUrl(owner, destination).get("shortCode").asText();

        mockMvc.perform(get("/" + code)
                        .header("Referer", "https://referrer.example/home")
                        .header("User-Agent", "curl/8.0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination));

        // The redirect published one click event with the captured metadata.
        assertThat(publisher.publishedEvents()).hasSize(1);
        ClickEventMessage event = publisher.publishedEvents().get(0);
        assertThat(event.shortCode()).isEqualTo(code);
        assertThat(event.referrer()).isEqualTo("https://referrer.example/home");
        assertThat(event.userAgent()).isEqualTo("curl/8.0");
        assertThat(event.ip()).isNotNull();
        assertThat(event.country()).isNull();
    }

    @Test
    void redirectStillRedirectsWhenClickPublishingFails() throws Exception {
        String owner = bearerToken();
        String destination = "https://example.com/phase8-fail";
        String code = createUrl(owner, destination).get("shortCode").asText();

        // Simulate a broker outage: the next publish throws inside the service.
        publisher.failNextPublish();

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination));
    }

    @Test
    void analyticsReturnsRealPersistedClickCountForOwner() throws Exception {
        String owner = bearerToken();
        String code = createUrl(owner, "https://example.com/phase8-count").get("shortCode").asText();
        Url url = persistedUrl(code);

        // Simulate what the Kafka consumer would persist (asynchronously).
        persistClick(url, "c1" + (++sequence));
        persistClick(url, "c2" + (++sequence));
        persistClick(url, "c3" + (++sequence));

        mockMvc.perform(get("/api/v1/urls/" + url.getId() + "/analytics")
                        .header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3));
    }

    @Test
    void analyticsReturnsZeroWhenNoClicksPersisted() throws Exception {
        String owner = bearerToken();
        String code = createUrl(owner, "https://example.com/phase8-zero").get("shortCode").asText();
        Url url = persistedUrl(code);

        mockMvc.perform(get("/api/v1/urls/" + url.getId() + "/analytics")
                        .header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    void analyticsForAnotherUsersUrlReturns404() throws Exception {
        String ownerA = bearerToken();
        String ownerB = bearerToken();
        String code = createUrl(ownerA, "https://example.com/phase8-idor").get("shortCode").asText();
        Url url = persistedUrl(code);
        persistClick(url, "idor1");

        mockMvc.perform(get("/api/v1/urls/" + url.getId() + "/analytics")
                        .header(HttpHeaders.AUTHORIZATION, ownerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsForUnknownUrlReturns404() throws Exception {
        String owner = bearerToken();
        mockMvc.perform(get("/api/v1/urls/999999/analytics")
                        .header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/urls/1/analytics"))
                .andExpect(status().isUnauthorized());
    }
}