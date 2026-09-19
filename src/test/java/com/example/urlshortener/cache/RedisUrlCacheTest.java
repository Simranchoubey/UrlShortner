package com.example.urlshortener.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RedisUrlCache}: TTL strategy, JSON round-tripping, and
 * graceful swallowing of Redis failures. The StringRedisTemplate is mocked; no
 * real Redis connection is made (Docker is unavailable on this machine).
 */
class RedisUrlCacheTest {

    private static final String PREFIX = "url:";
    private static final Duration MAX_TTL = Duration.ofDays(7);

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisUrlCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        cache = new RedisUrlCache(redis, testObjectMapper(), fixedClock, PREFIX, MAX_TTL);
    }

    /**
     * Mirrors the Spring Boot auto-configured ObjectMapper (JavaTimeModule +
     * ISO-8601 dates instead of epoch timestamps) so serialization of
     * {@link CachedUrl#expiresAt()} round-trips as expected.
     */
    private static ObjectMapper testObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // --- get -----------------------------------------------------------------------------

    @Test
    void getReturnsParsedValueOnHit() {
        String json = "{\"originalUrl\":\"https://example.com/x\",\"expiresAt\":\"2035-06-01T00:00:00Z\"}";
        when(ops.get("url:abc123")).thenReturn(json);

        Optional<CachedUrl> result = cache.get("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().originalUrl()).isEqualTo("https://example.com/x");
        assertThat(result.get().expiresAt()).isEqualTo(Instant.parse("2035-06-01T00:00:00Z"));
    }

    @Test
    void getReturnsEmptyOnMiss() {
        when(ops.get("url:abc123")).thenReturn(null);

        assertThat(cache.get("abc123")).isEmpty();
    }

    @Test
    void getReturnsEmptyWhenRedisThrows() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        // Redis read failure → treated as a miss so the caller falls back to DB.
        assertThat(cache.get("abc123")).isEmpty();
    }

    // --- put / TTL -----------------------------------------------------------------------

    @Test
    void putStoresJsonWithDefaultTtlForNeverExpiringLink() {
        cache.put("abc123", new CachedUrl("https://example.com/x", null));

        verify(ops).set(eq("url:abc123"), anyString(),
                eq(MAX_TTL.toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void putCapsTtlAtMaxForFarFutureExpiry() {
        cache.put("abc123", new CachedUrl("https://example.com/x", Instant.parse("2099-01-01T00:00:00Z")));

        // min(farFuture - now, 7 days) = 7 days.
        verify(ops).set(eq("url:abc123"), anyString(),
                eq(MAX_TTL.toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void putUsesRemainingTtlWhenExpiryIsSoon() {
        // 2 days after the fixed "now" (2030-01-01).
        cache.put("abc123", new CachedUrl("https://example.com/x", Instant.parse("2030-01-03T00:00:00Z")));

        verify(ops).set(eq("url:abc123"), anyString(),
                eq(Duration.ofDays(2).toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void putDoesNotStoreAlreadyExpiredValue() {
        cache.put("abc123", new CachedUrl("https://example.com/x", Instant.parse("2020-01-01T00:00:00Z")));

        // put returns early for an already-expired value — never touches Redis.
        verify(redis, never()).opsForValue();
    }

    @Test
    void putSwallowsRedisWriteFailure() {
        doThrow(new RuntimeException("connection refused"))
                .when(ops).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        cache.put("abc123", new CachedUrl("https://example.com/x", null));

        // No exception propagates — cache failure is a silent no-op to callers.
    }

    // --- evict ---------------------------------------------------------------------------

    @Test
    void evictDeletesKey() {
        cache.evict("abc123");
        verify(redis).delete("url:abc123");
    }

    @Test
    void evictSwallowsRedisFailureSoDeletionStillSucceeds() {
        org.mockito.Mockito.doThrow(new RuntimeException("connection refused"))
                .when(redis).delete("url:abc123");

        // A Redis eviction failure during URL deletion must never propagate to the
        // caller — the database delete in PostgreSQL is the source of truth and has
        // already committed by the time evict() runs (§7 of the plan).
        cache.evict("abc123");
    }
}