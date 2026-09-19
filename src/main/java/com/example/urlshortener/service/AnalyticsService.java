package com.example.urlshortener.service;

import com.example.urlshortener.repository.ClickEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads persisted click events to answer analytics questions (Phase 8).
 *
 * <p>This service deliberately <em>reads only</em>: it aggregates whatever the
 * {@code ClickEventConsumer} has asynchronously persisted into the
 * {@code click_events} table (PostgreSQL is the source of truth, IMPLEMENTATION_PLAN.md
 * §13). It holds no opinion about ownership — the caller ({@link UrlService}) has
 * already verified the URL belongs to the requesting user and mapped a foreign/unknown
 * URL to 404 before this count is ever reached.
 */
@Service
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    /**
     * Returns the number of recorded clicks for a URL. Zero when no events have been
     * persisted yet (e.g. none occurred, or the consumer is still catching up after a
     * broker outage — the count reflects what has actually been persisted, never
     * blocking or guessing).
     *
     * @param urlId the id of an <em>owned</em> URL (ownership checked by the caller)
     * @return the persisted click count
     */
    @Transactional(readOnly = true)
    public long countClicksForUrl(Long urlId) {
        return countClicks(urlId);
    }

    /**
     * Counts rows directly via the repository rather than loading entities into memory.
     */
    private long countClicks(Long urlId) {
        return clickEventRepository.countByUrlId(urlId);
    }
}