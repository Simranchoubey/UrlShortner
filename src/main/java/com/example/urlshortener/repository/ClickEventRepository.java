package com.example.urlshortener.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.urlshortener.domain.ClickEvent;

/**
 * Data access for {@link ClickEvent}s (click analytics).
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /**
     * Lists analytics events for a given URL (the "analytics for a link" view).
     */
    List<ClickEvent> findByUrlId(Long urlId);

    /**
     * Counts the recorded clicks for a URL — the Phase 8 analytics aggregation used by
     * {@link com.example.urlshortener.service.AnalyticsService}. Counts only what has
     * actually been persisted (asynchronously via the Kafka consumer), so it never blocks
     * the redirect path and never reflects un-persisted events.
     */
    long countByUrlId(Long urlId);
}