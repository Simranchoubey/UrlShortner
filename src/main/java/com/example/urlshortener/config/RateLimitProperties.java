package com.example.urlshortener.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-driven rate-limit configuration (Phase 9).
 *
 * <p>Backed by the {@code app.rate-limit.*} keys in {@code application.yml} (see
 * {@code .env.example} for the corresponding environment variables). All limits
 * default to developer-friendly values consistent with this project's scale —
 * they are intentionally not hard-coded into the filter or service. Rate limiting
 * can be switched off entirely with {@code app.rate-limit.enabled=false} (test
 * profile does exactly this indirectly by using small limits; the flag is the
 * master on/off switch).
 *
 * @param enabled              master switch; when {@code false} no request is ever limited
 * @param window               duration of one fixed time window (e.g. 60s)
 * @param createUrlLimit       max {@code POST /api/v1/urls} calls per window per user/IP
 * @param loginLimit           max {@code POST /api/v1/auth/login} calls per window per IP
 * @param registerLimit        max {@code POST /api/v1/auth/register} calls per window per IP
 * @param redirectEnabled      whether to also rate-limit the public redirect endpoint
 *                             ({@code GET /{shortCode}}). Off by default: the redirect is the
 *                             performance-critical path and must not pay a limiter lookup unless
 *                             explicitly enabled (IMPLEMENTATION_PLAN.md §12 / Phase-9 task).
 * @param redirectLimit        max redirects per window per IP (only honoured when {@code redirectEnabled})
 * @param prefix               Redis key namespace prefix ({@code rate-limit:}), used in
 *                             {@code rate-limit:{scope}:{identifier}} keys
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Duration window,
        int createUrlLimit,
        int loginLimit,
        int registerLimit,
        boolean redirectEnabled,
        int redirectLimit,
        String prefix) {

    /** Sensible defaults when {@code app.rate-limit.*} is absent from configuration. */
    public static RateLimitProperties defaults() {
        return new RateLimitProperties(true, Duration.ofSeconds(60), 30, 10, 5, false, 60, "rate-limit:");
    }
}