package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.ratelimit.InMemoryRateLimiter;
import com.example.urlshortener.ratelimit.RateLimiter;
import com.example.urlshortener.ratelimit.RateLimitScope;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RateLimitService} (Phase 9): limit enforcement at
 * below/at/above threshold, global disable, the {@code Retry-After} value, and the
 * rate-limit key namespace (distinct from URL cache keys, separate per scope and
 * per identifier).
 */
class RateLimitServiceTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private RateLimitService service(RateLimiter limiter, RateLimitProperties props) {
        return new RateLimitService(limiter, props);
    }

    private RateLimitProperties props() {
        return RateLimitProperties.defaults();
    }

    private RateLimitProperties disabled() {
        RateLimitProperties d = RateLimitProperties.defaults();
        return new RateLimitProperties(
                false, d.window(), d.createUrlLimit(), d.loginLimit(),
                d.registerLimit(), d.redirectEnabled(), d.redirectLimit(), d.prefix());
    }

    // --- limit enforcement -----------------------------------------------------

    @Test
    void allowsRequestBelowLimit() {
        RateLimiter limiter = new InMemoryRateLimiter();
        RateLimitService svc = service(limiter, props());

        svc.check(RateLimitScope.CREATE_URL, "1", 30, WINDOW); // count 1
        svc.check(RateLimitScope.CREATE_URL, "1", 30, WINDOW); // count 2

        // No exception → allowed.
        assertThat(true).isTrue();
    }

    @Test
    void allowsRequestAtLimit() {
        RateLimiter limiter = new InMemoryRateLimiter();
        RateLimitService svc = service(limiter, props());

        for (int i = 0; i < 30; i++) {
            svc.check(RateLimitScope.CREATE_URL, "1", 30, WINDOW);
        }
        assertThat(true).isTrue(); // the 30th is allowed (== limit)
    }

    @Test
    void rejectsRequestAboveLimit() {
        RateLimiter limiter = new InMemoryRateLimiter();
        RateLimitService svc = service(limiter, props());

        assertThatThrownBy(() -> {
            for (int i = 0; i < 31; i++) {
                svc.check(RateLimitScope.CREATE_URL, "1", 30, WINDOW);
            }
        }).isInstanceOf(RateLimitExceededException.class)
          .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                  .isGreaterThanOrEqualTo(1));
    }

    @Test
    void disabledRateLimitingAllowsEverythingWithoutCounting() {
        RateLimiter limiter = org.mockito.Mockito.mock(RateLimiter.class);
        RateLimitService svc = service(limiter, disabled());

        svc.check(RateLimitScope.CREATE_URL, "1", 1, WINDOW);
        svc.check(RateLimitScope.CREATE_URL, "1", 1, WINDOW);

        verify(limiter, never()).incrementAndGet(any(), any());
    }

    // --- key namespacing ------------------------------------------------------

    @Test
    void keysUseRateLimitNamespaceNotUrlCacheNamespace() {
        RateLimitService svc = service(new InMemoryRateLimiter(), props());

        assertThat(svc.key(RateLimitScope.CREATE_URL, "42"))
                .startsWith("rate-limit:")
                .isEqualTo("rate-limit:create-url:42");
        assertThat(svc.key(RateLimitScope.REDIRECT, "1.2.3.4"))
                .isEqualTo("rate-limit:redirect:1.2.3.4");
    }

    @Test
    void differentIdentifiersHaveIndependentCounters() {
        RateLimiter limiter = new InMemoryRateLimiter();
        RateLimitService svc = service(limiter, props());

        svc.check(RateLimitScope.CREATE_URL, "alice", 2, WINDOW); // alice: 1
        svc.check(RateLimitScope.CREATE_URL, "alice", 2, WINDOW); // alice: 2
        svc.check(RateLimitScope.CREATE_URL, "bob", 2, WINDOW);   // bob: 1 (bob unaffected by alice's count)

        // bob has used 1 of 2 → still allowed; alice is now at her limit.
        assertThatThrownBy(() -> svc.check(RateLimitScope.CREATE_URL, "alice", 2, WINDOW));
    }

    @Test
    void differentEndpointsHaveSeparateCounters() {
        RateLimiter limiter = new InMemoryRateLimiter();
        RateLimitService svc = service(limiter, props());

        // Same identifier, different scopes → independent limits.
        svc.check(RateLimitScope.CREATE_URL, "1.2.3.4", 2, WINDOW);
        svc.check(RateLimitScope.CREATE_URL, "1.2.3.4", 2, WINDOW); // create-url at 2/2
        svc.check(RateLimitScope.AUTH_LOGIN, "1.2.3.4", 1, WINDOW); // auth-login at 1/1, allowed
    }

    // --- delegation / identifier passing --------------------------------------

    @Test
    void passesIdentifierAndWindowToLimiter() {
        RateLimiter limiter = org.mockito.Mockito.mock(RateLimiter.class);
        when(limiter.incrementAndGet(eq("rate-limit:create-url:7"), eq(WINDOW))).thenReturn(1L);
        RateLimitService svc = service(limiter, props());

        svc.check(RateLimitScope.CREATE_URL, "7", 30, WINDOW);

        verify(limiter).incrementAndGet(eq("rate-limit:create-url:7"), eq(WINDOW));
    }
}