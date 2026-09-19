package com.example.urlshortener.controller;

import com.example.urlshortener.exception.ApiError;
import com.example.urlshortener.service.UrlService;
import com.example.urlshortener.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redirect endpoint (Phase 4), now with async click publishing (Phase 8).
 *
 * <p>{@code GET /{shortCode}} resolves the short code against the cache/database and
 * answers with a {@code 302 Found} whose {@code Location} header is the original
 * long URL. The controller stays thin — all lookup/expiry logic lives in
 * {@link UrlService#resolveShortCode(String, String, String, String)}.
 *
 * <p>Security: the destination is merely echoed in the {@code Location} header; the
 * server makes no outbound HTTP request to it (no SSRF surface).
 *
 * <p>Phase 8: request metadata ({@code Referer}, {@code User-Agent}, client IP) is
 * captured and passed to the service, which publishes an asynchronous click event
 * fire-and-forget. The redirect never waits for (or fails on) analytics.
 */
@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Redirects a short code (or custom alias, which shares the {@code short_code}
     * value) to its original URL.
     *
     * <p>Not-found produces {@code 404} and expired {@code 410} via the global
     * exception handlers. Client IP is taken best-effort from the request (missing
     * headers/IPs become {@code null}, never a redirect failure — §5).
     */
    @Operation(summary = "Redirect a short code to its original URL",
            description = "Public. Resolves a short code (or custom alias) and responds with 302 and the "
                    + "original long URL in the Location header. Unknown codes → 404; expired URLs → 410. "
                    + "Also publishes an asynchronous click event via Kafka for analytics. Optionally limited "
                    + "per client IP when redirect rate limiting is enabled (off by default).")
    @GetMapping("/{shortCode}")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Found — Location header carries the original URL",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Short code not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "410", description = "The short URL has expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Redirect rate limit exceeded (if enabled)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> redirect(
            @PathVariable("shortCode") String shortCode,
            HttpServletRequest request) {
        String originalUrl = urlService.resolveShortCode(
                shortCode,
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                ClientIpResolver.resolve(request));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}