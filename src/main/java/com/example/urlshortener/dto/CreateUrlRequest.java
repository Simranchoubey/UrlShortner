package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/urls}.
 *
 * <p>Presence/length checks are done here via Bean Validation (returned as
 * {@code 400}); deeper format, scheme, and control-character checks are done in
 * the service layer via {@link com.example.urlshortener.util.UrlValidator}.
 *
 * @param originalUrl the long URL to shorten (required, http/https, ≤ 2048 chars)
 * @param customAlias an optional caller-chosen alias (URL-safe, 3–12 chars)
 * @param expiresAt   an optional expiry timestamp; must be in the future
 */
public record CreateUrlRequest(
        @NotBlank(message = "originalUrl is required")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
        String originalUrl,

        @Size(min = 3, max = 12, message = "customAlias must be between 3 and 12 characters")
        @Pattern(regexp = "[A-Za-z0-9_-]+",
                message = "customAlias may only contain letters, digits, '-' and '_'")
        String customAlias,

        Instant expiresAt) {
}