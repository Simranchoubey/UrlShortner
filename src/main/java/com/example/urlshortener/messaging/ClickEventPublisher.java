package com.example.urlshortener.messaging;

/**
 * Publishes {@link ClickEventMessage}s produced by the redirect flow.
 *
 * <p>The redirect path depends only on this abstraction, so it is decoupled from
 * any particular messaging backend. Implementations must be <em>best-effort</em>:
 * a publish failure must never propagate and break the HTTP redirect. The real
 * implementation is a Kafka producer {@link KafkaClickEventPublisher}; a test
 * double is substituted under the {@code test} profile so integration tests do not
 * require a running Kafka broker.
 */
public interface ClickEventPublisher {

    /**
     * Best-effort publish of a click event. Implementations catch and log transport
     * failures rather than throwing, so the caller (the redirect path) is never
     * blocked or failed by messaging being unavailable.
     *
     * @param event the click event to publish; implementations should ignore null
     */
    void publishClick(ClickEventMessage event);
}