package com.example.urlshortener.dto;

/**
 * Response body for {@code GET /api/v1/urls/{id}/analytics}.
 *
 * <p>Phase 8: {@code totalClicks} is the real persisted click count for the URL,
 * asynchronously written by the Kafka consumer into {@code click_events} and read
 * back at request time (PostgreSQL is the source of truth). It is {@code 0} before
 * any click event has been recorded.
 *
 * @param totalClicks the number of recorded clicks for the URL ({@code 0} if none)
 */
public record UrlAnalyticsResponse(long totalClicks) {
}