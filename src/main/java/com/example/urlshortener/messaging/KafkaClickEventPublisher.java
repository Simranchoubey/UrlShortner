package com.example.urlshortener.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed {@link ClickEventPublisher}.
 *
 * <p>Sends each {@link ClickEventMessage} to the configured click-events topic
 * (default {@code url-click-events}) using the injected {@link KafkaTemplate}.
 *
 * <p><b>Failure handling (IMPLEMENTATION_PLAN.md §9):</b> publishing is strictly
 * fire-and-forget and best-effort. If the broker is unavailable, the template
 * raises a {@link RuntimeException} which is caught and logged (WARN) here — it is
 * never rethrown to the redirect request. The redirect path must remain 302 even
 * when Kafka is down; analytics simply lag while the log records the drop/buffer
 * (§20/§19 of the plan).
 *
 * <p>Disabled under the {@code test} profile, where a test double is substituted
 * so integration tests run without a Kafka broker.
 */
@Component
@Profile("!test")
public class KafkaClickEventPublisher implements ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickEventPublisher.class);

    private final KafkaTemplate<String, ClickEventMessage> kafkaTemplate;
    private final String topic;

    public KafkaClickEventPublisher(
            KafkaTemplate<String, ClickEventMessage> kafkaTemplate,
            @Value("${app.kafka.click-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publishClick(ClickEventMessage event) {
        if (event == null) {
            return;
        }
        try {
            kafkaTemplate.send(topic, event.shortCode(), event);
        } catch (RuntimeException ex) {
            // Best-effort: a Kafka outage must never break the redirect.
            log.warn("Failed to publish click event for shortCode '{}' to topic '{}': {}",
                    event.shortCode(), topic, ex.getMessage(), ex);
        }
    }
}