package com.example.urlshortener.controller;

import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.CreateUrlResponse;
import com.example.urlshortener.dto.UrlAnalyticsResponse;
import com.example.urlshortener.dto.UrlListResponse;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.exception.ApiError;
import com.example.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for managing short URLs.
 *
 * <p>Thin controller: parses/validates the HTTP request and delegates to
 * {@link UrlService}. The authenticated user id always comes from the
 * {@code Authentication} context set by the JWT filter — never from the URL path
 * or a request body — so ownership is enforced in the service layer against the
 * authenticated identity (§8 of the plan).
 *
 * <p>Covers creation (POST), listing (GET, paginated), single GET, deletion
 * (DELETE), and the analytics boundary (GET /{id}/analytics). Every endpoint in
 * this controller requires a valid JWT, documented in OpenAPI via the
 * {@code bearerAuth} scheme.
 */
@RestController
@RequestMapping("/api/v1/urls")
@SecurityRequirement(name = "bearerAuth")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Creates a new short URL.
     *
     * <p>Takes {@code originalUrl}, optional {@code customAlias}, and optional
     * {@code expiresAt}; returns {@code 201 Created} with the short code and the
     * fully-qualified short URL.
     *
     * <p>The owning user id is taken from the authenticated {@link Authentication}
     * context (set by the JWT filter) — never from the request body, so a client
     * cannot choose another user's id (§11 of the plan). Endpoint requires a JWT.
     */
    @Operation(summary = "Create a short URL",
            description = "Authenticated. Shortens a valid http/https originalUrl, optionally with a "
                    + "custom alias (3–12 URL-safe chars) and an optional future expiresAt. Returns 201 with "
                    + "the short code and fully-qualified short URL. 400 on bad input, 409 on a duplicate "
                    + "alias, 429 if the per-user create limit for the window is exceeded.")
    @PostMapping
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "URL created",
                    content = @Content(schema = @Schema(implementation = CreateUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid originalUrl, alias or expiresAt",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already in use",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-user create rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CreateUrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request,
            Authentication authentication) {
        // Phase 6: the JWT filter sets the principal to the authenticated user id.
        Long userId = (authentication == null) ? null : (Long) authentication.getPrincipal();
        CreateUrlResponse response = urlService.createUrl(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists the authenticated user's own short URLs, newest first, with
     * pagination ({@code ?page=0&size=20}; size is clamped to a maximum of 100).
     *
     * <p>Only the caller's URLs are returned — listing is inherently scoped to the
     * authenticated user id derived from the JWT.
     */
    @Operation(summary = "List the authenticated user's short URLs",
            description = "Authenticated. Returns the caller's own URLs, newest first, with pagination "
                    + "via ?page (zero-based) and ?size (clamped to a maximum of 100).")
    @GetMapping
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the caller's URLs",
                    content = @Content(schema = @Schema(implementation = UrlListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UrlListResponse listUrls(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            Authentication authentication) {
        Long userId = authenticatedUserId(authentication);
        return urlService.listUrls(userId, page, size);
    }

    /**
     * Returns one of the authenticated user's own URLs. A URL belonging to another
     * user (or an unknown id) is intentionally a {@code 404} — never exposed or
     * distinguished (§9 of the plan).
     */
    @Operation(summary = "Get one of the user's short URLs",
            description = "Authenticated. Returns one of the caller's own URLs by id. A URL owned by "
                    + "another user, or an unknown id, returns 404 (never enumerated).")
    @GetMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's URL",
                    content = @Content(schema = @Schema(implementation = UrlResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown id, or a URL owned by another user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UrlResponse getUrl(@PathVariable("id") Long id, Authentication authentication) {
        return urlService.getUrl(id, authenticatedUserId(authentication));
    }

    /**
     * Deletes one of the authenticated user's own URLs. Only the owner may delete
     * (the service enforces this); success returns {@code 204 No Content}. A URL
     * belonging to another user (or an unknown id) is a {@code 404}.
     */
    @Operation(summary = "Delete one of the user's short URLs",
            description = "Authenticated. Deletes one of the caller's own URLs by id. Success returns "
                    + "204. A URL owned by another user, or an unknown id, returns 404.")
    @DeleteMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted, no content body",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown id, or a URL owned by another user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> deleteUrl(@PathVariable("id") Long id, Authentication authentication) {
        urlService.deleteUrl(id, authenticatedUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the analytics boundary response for one of the authenticated user's
     * own URLs. <b>Phase 7:</b> only the endpoint and the ownership check — the
     * response is the zero-value shape until Phase 8 provides click aggregation.
     * Another user's URL (or an unknown id) is a {@code 404}.
     */
    @Operation(summary = "Get click analytics for one of the user's short URLs",
            description = "Authenticated. Returns the recorded click count (async, via Kafka) for one of "
                    + "the caller's own URLs. 0 before any click is recorded. A URL owned by another user, "
                    + "or an unknown id, returns 404.")
    @GetMapping("/{id}/analytics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Click analytics for the caller's URL",
                    content = @Content(schema = @Schema(implementation = UrlAnalyticsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown id, or a URL owned by another user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UrlAnalyticsResponse getAnalytics(@PathVariable("id") Long id, Authentication authentication) {
        return urlService.getAnalytics(id, authenticatedUserId(authentication));
    }

    /**
     * Extracts the authenticated user id from the security context. The JWT filter
     * guarantees this is populated on every protected endpoint (the entirety of
     * {@code /api/v1/urls} is authenticated), so null here would be a bug — but we
     * fail closed rather than delegate an unknown identity.
     */
    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new IllegalStateException("Authenticated principal is missing or malformed");
        }
        return id;
    }
}