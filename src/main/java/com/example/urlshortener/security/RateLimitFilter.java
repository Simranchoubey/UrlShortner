package com.example.urlshortener.security;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.exception.ApiError;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.ratelimit.RateLimitScope;
import com.example.urlshortener.service.RateLimitService;
import com.example.urlshortener.util.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies Redis-backed rate limiting at the HTTP boundary (Phase 9).
 *
 * <p>Runs as part of the Spring Security filter chain, <em>after</em> the JWT
 * filter, so the authenticated identity is available in the
 * {@link SecurityContextHolder} but <em>before</em> the controllers. Only the
 * configured endpoints are counted: {@code POST /api/v1/urls} (per authenticated
 * user id), {@code POST /api/v1/auth/register} and {@code POST /api/v1/auth/login}
 * (per client IP), and the public redirect {@code GET /{shortCode}} (per IP,
 * gated on {@code app.rate-limit.redirect-enabled} which defaults to off so the
 * performance-critical redirect path is not slowed).
 *
 * <p>The filter holds no Redis details — it only derives an identifier and hands
 * off to {@link RateLimitService}, which performs the atomic counter increment and
 * may throw {@link RateLimitExceededException}. Because this filter runs in the
 * servlet chain (outside MVC), that exception cannot be caught by
 * {@code @RestControllerAdvice}, so the filter itself renders the standard
 * {@link ApiError} body as a {@code 429} with a {@code Retry-After} header — the
 * same response shape the global exception handler would produce for a controller
 * throw. Requests for unrelated endpoints (health checks, list/get/delete URLs,
 * analytics) are passed through untouched and never touch the rate-limit store.
 *
 * <p>Authentication/business logic is untouched: the authenticated identity always
 * comes from {@link SecurityContextHolder}, never from request parameters or
 * bodies, so rate limiting cannot bypass or interfere with authorization.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        route(request, response, filterChain);
    }

    /**
     * Matches the request to a rate-limit scope and applies it if applicable;
     * otherwise the request flows through untouched. The counter is only touched
     * for the protected endpoints — never for health checks or the other URL APIs.
     */
    private void route(HttpServletRequest request,
                       HttpServletResponse response,
                       FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            // Master switch off → rate limiting is a complete no-op (§5).
            filterChain.doFilter(request, response);
            return;
        }
        try {
            applyLimitsOrPassthrough(request, response, filterChain);
        } catch (RateLimitExceededException ex) {
            respondTooManyRequests(response, ex);
        }
    }

    /**
     * Applies the applicable rate limit for the request and then continues the
     * chain; throws {@link RateLimitExceededException} when the limit is exceeded.
     */
    private void applyLimitsOrPassthrough(HttpServletRequest request,
                                          HttpServletResponse response,
                                          FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // POST /api/v1/urls — per authenticated user id (the one endpoint in the
        // whole /api/v1/urls tree worth protecting against abuse: creation).
        if (HttpMethod.POST.matches(method) && uri.equals("/api/v1/urls")) {
            Long userId = authenticatedUserId();
            if (userId != null) {
                rateLimitService.check(RateLimitScope.CREATE_URL,
                        String.valueOf(userId), properties.createUrlLimit(), properties.window());
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Unauthenticated authentication endpoints — per client IP.
        if (HttpMethod.POST.matches(method) && uri.equals("/api/v1/auth/register")) {
            rateLimitService.check(RateLimitScope.AUTH_REGISTER,
                    ClientIpResolver.resolve(request), properties.registerLimit(), properties.window());
            filterChain.doFilter(request, response);
            return;
        }
        if (HttpMethod.POST.matches(method) && uri.equals("/api/v1/auth/login")) {
            rateLimitService.check(RateLimitScope.AUTH_LOGIN,
                    ClientIpResolver.resolve(request), properties.loginLimit(), properties.window());
            filterChain.doFilter(request, response);
            return;
        }

        // Public redirect — only when explicitly enabled (off by default so the
        // performance-critical redirect path pays no limiter lookup).
        if (properties.redirectEnabled()
                && HttpMethod.GET.matches(method)
                && matchesRedirect(uri)) {
            rateLimitService.check(RateLimitScope.REDIRECT,
                    ClientIpResolver.resolve(request), properties.redirectLimit(), properties.window());
        }

        filterChain.doFilter(request, response);
    }

    /** Reads the authenticated user id from the security context, if present. */
    private Long authenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return (principal instanceof Long id) ? id : null;
    }

    /**
     * A "redirect" is any single-segment GET path not matching another API path
     * (e.g. {@code /abc123}). We deliberately exclude Actuator roots and the
     * versioned API so health probes and API paths are never treated as short codes.
     */
    private boolean matchesRedirect(String uri) {
        if (uri.equals("/") || uri.startsWith("/api/") || uri.startsWith("/actuator/")) {
            return false;
        }
        String trimmed = uri.startsWith("/") ? uri.substring(1) : uri;
        return !trimmed.isEmpty() && !trimmed.contains("/");
    }

    /**
     * Renders the standard {@link ApiError} body as a {@code 429} with a
     * {@code Retry-After} header. Never leaks internal details (Redis host,
     * counter values, exception stack). If the body cannot be serialized we fall
     * back to a minimal plain 429 so the client still sees the right status.
     */
    private void respondTooManyRequests(HttpServletResponse response,
                                        RateLimitExceededException ex) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}