package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.Url;
import com.example.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Web-layer integration tests for the Phase 3 URL-creation endpoint
 * ({@code POST /api/v1/urls}).
 *
 * <p>Since Phase 6, URL creation requires a JWT, so every request is made with a
 * token obtained by registering + logging in a fresh user. Runs against the
 * in-memory H2 database (see src/test/resources/application.yml) because
 * Docker/PostgreSQL is not available on this machine. These tests exercise the
 * full HTTP → controller → service → repository path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlCreationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    private static int sequence = 0;

    private String body(String originalUrl) {
        return "{ \"originalUrl\": \"" + originalUrl + "\", \"customAlias\": null, \"expiresAt\": null }";
    }

    /** Registers + logs in a fresh user and returns the bearer token. */
    private String bearerToken() throws Exception {
        String email = "urlcreation" + (++sequence) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
        return "Bearer " + token;
    }

    /** Registers a fresh user and returns the new account's id. */
    private long registeredUserId() throws Exception {
        String email = "owner" + (++sequence) + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();
    }

    /** Logs in an already-registered user and returns the bearer token. */
    private String login(String email) throws Exception {
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"" + email + "\", \"password\": \"securePassword123\" }"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void createUrlReturns201WithShortCodeAndShortUrlAndPersists() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/very/long/path")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "http://localhost:8080/[a-zA-Z0-9]+")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/very/long/path"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String shortCode = json.get("shortCode").asText();

        // URL is actually persisted and retrievable by short code (repository phase).
        Url persisted = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(persisted.getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
    }

    @Test
    void customAliasIsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/x\","
                                + " \"customAlias\": \"my-project\", \"expiresAt\": null }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-project"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/my-project"));
    }

    @Test
    void duplicateAliasReturns409() throws Exception {
        String token = bearerToken();
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/a\","
                                + " \"customAlias\": \"taken-alias\", \"expiresAt\": null }"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/b\","
                                + " \"customAlias\": \"taken-alias\", \"expiresAt\": null }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not a valid url")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void emptyUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedSchemeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ftp://example.com/file")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedUrlReturns400() throws Exception {
        String huge = "https://example.com/" + "x".repeat(2100);
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(huge)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAliasReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/x\","
                                + " \"customAlias\": \"a!@#\", \"expiresAt\": null }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expirationInThePastReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/x\","
                                + " \"customAlias\": null, \"expiresAt\": \"2020-01-01T00:00:00Z\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createdUrlStoresAuthenticatedUserIdAndCannotBeOverridden() throws Exception {
        long owner = registeredUserId();

        // Log the same user back in to obtain a token bound to that account id.
        String email = "owner" + sequence + "@example.com";
        String token = login(email);

        // The request body intentionally does NOT contain a userId field — the owner
        // always comes from the JWT, so a client can never choose another user's id.
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/owner-check\","
                                + " \"customAlias\": null, \"expiresAt\": null }"))
                .andExpect(status().isCreated())
                .andReturn();

        String shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();
        Url persisted = urlRepository.findByShortCode(shortCode).orElseThrow();

        // The owning user recorded is the authenticated user's id.
        assertThat(persisted.getUser()).isNotNull();
        assertThat(persisted.getUser().getId()).isEqualTo(owner);
    }
}