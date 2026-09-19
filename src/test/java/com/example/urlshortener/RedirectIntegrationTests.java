package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.Url;
import com.example.urlshortener.repository.UrlRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer integration tests for the Phase 4 public redirect endpoint
 * ({@code GET /{shortCode}}).
 *
 * <p>Runs against the in-memory H2 database (see src/test/resources/application.yml)
 * because Docker/PostgreSQL is not available on this machine. URL rows are saved
 * directly via the repository so expiry values can be controlled precisely; the
 * tests exercise the full HTTP → controller → service → repository → 302 path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedirectIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    private Url save(String shortCode, String originalUrl, OffsetDateTime expiresAt) {
        Url url = new Url();
        url.setShortCode(shortCode);
        url.setOriginalUrl(originalUrl);
        url.setExpiresAt(expiresAt);
        return urlRepository.saveAndFlush(url);
    }

    private static OffsetDateTime utcSeconds(long epochSecond) {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    @Test
    void validShortCodeRedirectsWithLocationHeader() throws Exception {
        save("rd00001", "https://example.com/very/long/path", null);

        mockMvc.perform(get("/rd00001"))
                .andExpect(status().isFound())                      // 302
                .andExpect(header().string("Location", "https://example.com/very/long/path"));
    }

    @Test
    void futureExpiryRedirectsSuccessfully() throws Exception {
        // expiry far in the future relative to the test's render time.
        save("rd00002", "https://example.com/still-valid", utcSeconds(4_200_000_000L));

        mockMvc.perform(get("/rd00002"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/still-valid"));
    }

    @Test
    void neverExpiringRedirectsSuccessfully() throws Exception {
        save("rd00003", "https://example.com/never-expires", null);

        mockMvc.perform(get("/rd00003"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/never-expires"));
    }

    @Test
    void unknownShortCodeReturns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void expiredShortCodeReturns410() throws Exception {
        // expiry far in the past relative to the test's render time.
        save("rd00005", "https://example.com/expired", utcSeconds(1_577_836_800L)); // 2020-01-01

        mockMvc.perform(get("/rd00005"))
                .andExpect(status().isGone())                        // 410
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void destinationWithQueryFragmentAndPathIsPreserved() throws Exception {
        String destination =
                "https://example.com/deep/path?sort=desc&filter=a%20b#top";
        save("rd00006", destination, null);

        mockMvc.perform(get("/rd00006"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination));
    }

    @Test
    void customAliasPathRedirectsToOriginalUrl() throws Exception {
        // A custom alias is stored as the short_code value too (Phase 3 design),
        // so it resolves through the same redirect lookup. Both fields are set to
        // mirror how aliased URLs are actually persisted. This code is distinct
        // from values the creation tests use, because the shared in-memory H2
        // database persists across @SpringBootTest classes.
        Url url = new Url();
        url.setShortCode("alpha-beta");
        url.setOriginalUrl("https://example.com/project-home");
        url.setCustomAlias("alpha-beta");
        urlRepository.saveAndFlush(url);

        mockMvc.perform(get("/alpha-beta"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/project-home"));

        // sanity-check it was actually persisted as requested.
        assertThat(urlRepository.findByShortCode("alpha-beta")).isPresent();
    }
}