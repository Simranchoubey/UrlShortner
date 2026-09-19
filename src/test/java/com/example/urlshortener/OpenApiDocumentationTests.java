package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the OpenAPI/Swagger documentation (Phase 10):
 * <ul>
 *   <li>{@code GET /v3/api-docs} is reachable without authentication;</li>
 *   <li>all public and protected endpoints appear in the spec;</li>
 *   <li>the JWT {@code bearerAuth} scheme is declared and applied to the protected
 *       {@code /api/v1/urls} controller but NOT to the public auth/redirect endpoints;</li>
 *   <li>request/response schemas and the standard {@link com.example.urlshortener.exception.ApiError}
 *       error body are exposed.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode spec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Returns the OpenAPI "security" array requirement for a path's given operation, or null. */
    private JsonNode securityOf(JsonNode spec, String path, String method) {
        JsonNode op = spec.path("paths").path(path).path(method);
        assertThat(op.isMissingNode())
                .as("expected %s %s to be documented", method.toUpperCase(), path)
                .isFalse();
        return op.get("security");
    }

    @Test
    void specIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        // Reached already via spec(); a 200 here confirms it is not blocked by Spring Security.
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void documentsAllPublicAndProtectedEndpoints() throws Exception {
        JsonNode paths = spec().path("paths");
        assertThat(paths.has("/api/v1/auth/register")).isTrue();
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/urls")).isTrue();
        assertThat(paths.has("/api/v1/urls/{id}")).isTrue();
        assertThat(paths.has("/api/v1/urls/{id}/analytics")).isTrue();
        assertThat(paths.has("/{shortCode}")).isTrue();
    }

    @Test
    void declaresTheJwtBearerScheme() throws Exception {
        JsonNode schemes = spec().path("components").path("securitySchemes");
        JsonNode bearer = schemes.get("bearerAuth");
        assertThat(bearer).as("bearerAuth scheme is declared").isNotNull();
        assertThat(bearer.get("type").asText()).isEqualTo("http");
        assertThat(bearer.get("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.get("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void protectedUrlEndpointsRequireTheBearerScheme() throws Exception {
        JsonNode spec = spec();
        // Every /api/v1/urls operation must carry the bearerAuth security requirement.
        requireAuth(spec, "/api/v1/urls", "post");
        requireAuth(spec, "/api/v1/urls", "get");
        requireAuth(spec, "/api/v1/urls/{id}", "get");
        requireAuth(spec, "/api/v1/urls/{id}", "delete");
        requireAuth(spec, "/api/v1/urls/{id}/analytics", "get");
    }

    @Test
    void publicEndpointsDoNotRequireAuthentication() throws Exception {
        JsonNode spec = spec();
        assertThat(securityOf(spec, "/api/v1/auth/register", "post")).isNull();
        assertThat(securityOf(spec, "/api/v1/auth/login", "post")).isNull();
        assertThat(securityOf(spec, "/{shortCode}", "get")).isNull();
    }

    @Test
    void documentsRegisterLoginSchemas() throws Exception {
        JsonNode components = spec().path("components").path("schemas");
        assertThat(components.has("RegisterRequest")).isTrue();
        assertThat(components.has("RegisterResponse")).isTrue();
        assertThat(components.has("LoginRequest")).isTrue();
        assertThat(components.has("LoginResponse")).isTrue();
    }

    @Test
    void documentsUrlCreateAndManagementSchemas() throws Exception {
        JsonNode components = spec().path("components").path("schemas");
        assertThat(components.has("CreateUrlRequest")).isTrue();
        assertThat(components.has("CreateUrlResponse")).isTrue();
        assertThat(components.has("UrlResponse")).isTrue();
        assertThat(components.has("UrlListResponse")).isTrue();
        assertThat(components.has("UrlAnalyticsResponse")).isTrue();
    }

    @Test
    void documentsTheStandardErrorBody() throws Exception {
        JsonNode components = spec().path("components").path("schemas");
        assertThat(components.has("ApiError")).isTrue();
    }

    /** Asserts the given operation carries a non-empty security bearing "bearerAuth". */
    private void requireAuth(JsonNode spec, String path, String method) {
        JsonNode security = securityOf(spec, path, method);
        assertThat(security).as("expected %s %s to require authentication", method.toUpperCase(), path)
                .isNotNull();
        assertThat(security.get(0).has("bearerAuth")).isTrue();
    }
}