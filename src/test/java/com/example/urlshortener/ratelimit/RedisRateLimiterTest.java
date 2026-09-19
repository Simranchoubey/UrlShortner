package com.example.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RedisRateLimiter} (Phase 9). The StringRedisTemplate is
 * mocked — no real Redis is contacted, because Docker/Redis is unavailable on this
 * machine. Covers the atomic increment, the window-expiry handling, and the
 * fail-open behaviour on a Redis outage.
 */
class RedisRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ops = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(ops);
        limiter = new RedisRateLimiter(redis);
    }

    @Test
    void incrementsCounterAtomically() {
        when(ops.increment(anyString())).thenReturn(3L);

        long count = limiter.incrementAndGet("rate-limit:create-url:42", Duration.ofSeconds(60));

        assertThat(count).isEqualTo(3L);
        verify(redis).opsForValue();
        verify(ops).increment("rate-limit:create-url:42");
    }

    @Test
    void setsExpiryOnFirstIncrementOfWindow() {
        when(ops.increment(anyString())).thenReturn(1L);
        Duration window = Duration.ofSeconds(60);

        limiter.incrementAndGet("rate-limit:auth-login:1.2.3.4", window);

        verify(redis).expire("rate-limit:auth-login:1.2.3.4", window);
    }

    @Test
    void doesNotResetExpiryWhenWindowAlreadyActive() {
        when(ops.increment(anyString())).thenReturn(5L); // >= 2 → window already established

        limiter.incrementAndGet("rate-limit:create-url:9", Duration.ofSeconds(60));

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void failsOpenWhenRedisIncrementsThrows() {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("connection refused"));

        long count = limiter.incrementAndGet("rate-limit:create-url:1", Duration.ofSeconds(60));

        // Fail open: returns 0 so the caller treats this as "no usage recorded".
        assertThat(count).isZero();
    }

    @Test
    void failsOpenWhenRedisExpireThrows() {
        when(ops.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection reset"));

        long count = limiter.incrementAndGet("rate-limit:create-url:1", Duration.ofSeconds(60));

        // Fail-open: any Redis error degrades to 0 so the request is allowed.
        assertThat(count).isZero();
    }

    @Test
    void treatsNullIncrementAsZero() {
        when(ops.increment(anyString())).thenReturn(null);

        long count = limiter.incrementAndGet("rate-limit:create-url:1", Duration.ofSeconds(60));

        assertThat(count).isZero();
    }
}