package com.example.urlshortener.messaging;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes click events from the Kafka topic and persists them into the existing
 * {@code click_events} table (via {@link ClickEventRepository}), keeping
 * PostgreSQL as the source of truth for analytics (IMPLEMENTATION_PLAN.md §13).
 *
 * <p>Responsibilities (IMPLEMENTATION_PLAN.md §6): receive → validate/deserialize
 * → persist {@link ClickEvent} → log failures appropriately. Processing happens
 * asynchronously on the Kafka thread, never on the HTTP redirect thread.
 *
 * <p><b>Failure handling:</b> a malformed or unprocessable event is logged and
 * skipped rather than crashing the consumer or the whole application.
 * Deliberately, no analytics aggregation is performed here — that is a later phase;
 * this phase only persists raw click rows (§6).
 *
 * <p>Only active when Kafka is <b>enabled</b> ({@code app.kafka.enabled} = true) and
 * outside the {@code test} profile — so the context loads without a broker when
 * Kafka is disabled.
 */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
@Profile("!test")
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;

    public ClickEventConsumer(ClickEventRepository clickEventRepository, UrlRepository urlRepository) {
        this.clickEventRepository = clickEventRepository;
        this.urlRepository = urlRepository;
    }

    /**
     * Persists a single click event. The {@code url} FK is a lazy reference resolved
     * by id; the message may carry {@code urlId} directly (database-path redirects)
     * or only a {@code shortCode} (cache-hit redirects), so this method resolves the
     * id from {@code shortCode} when it is not supplied:
     *
     * <ol>
     *   <li>Ignore nulls (defensive).</li>
     *   <li>Determine the target {@link Url}: prefer {@code urlId} if present, else
     *       resolve {@code shortCode}. If it no longer exists, drop the event with a
     *       log (the URL may have been deleted between publish and consume — the FK
     *       would reject the insert).</li>
     *   <li>Map the message onto a new {@link ClickEvent} and persist.</li>
     *   <li>Log failures without rethrowing (a poison message must not kill the
     *       whole consumer thread).</li>
     * </ol>
     */
    @KafkaListener(topics = "${app.kafka.click-topic}", groupId = "${app.kafka.consumer-group}")
    public void onEvent(ClickEventMessage event) {
        if (event == null) {
            log.warn("Received a null click event; skipping");
            return;
        }
        try {
            Url url = resolveUrl(event);
            if (url == null) {
                log.warn("Dropping click event: no URL for shortCode '{}' / urlId {}",
                        event.shortCode(), event.urlId());
                return;
            }

            ClickEvent entity = new ClickEvent();
            entity.setUrl(url);
            entity.setShortCode(event.shortCode());
            entity.setEventTime(event.eventTime());
            entity.setReferrer(event.referrer());
            entity.setUserAgent(event.userAgent());
            entity.setIp(event.ip());
            entity.setCountry(event.country());

            clickEventRepository.save(entity);
            log.debug("Persisted click event for urlId {} shortCode '{}'", url.getId(), event.shortCode());
        } catch (RuntimeException ex) {
            // A malformed/invalid event must not crash the consumer or the app.
            log.error("Failed to persist click event for shortCode '{}': {}",
                    event.shortCode(), ex.getMessage(), ex);
        }
    }

    /** Resolves the target {@link Url} by explicit id, falling back to shortCode lookup. */
    private Url resolveUrl(ClickEventMessage event) {
        if (event.urlId() != null) {
            return urlRepository.findById(event.urlId()).orElse(null);
        }
        return urlRepository.findByShortCode(event.shortCode()).orElse(null);
    }
}