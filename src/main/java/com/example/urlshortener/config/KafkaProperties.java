package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka settings for the asynchronous click-analytics transport (Phase 8).
 *
 * <p>All values are environment-driven with sensible local defaults; nothing is
 * hardcoded for production (IMPLEMENTATION_PLAN.md §17 / task §1). Mapped from the
 * {@code app.kafka.*} keys (environment override: {@code APP_KAFKA_*}) declared
 * in {@code application.yml}.
 *
 * @param bootstrapServers comma-separated {@code host:port} list of brokers
 *                         (default {@code localhost:9092})
 * @param clickTopic       the topic click events are published to/consumed from
 *                         (default {@code url-click-events})
 * @param consumerGroup    the consumer group id (default {@code url-analytics})
 * @param enabled          master switch; when false the listener container and the
 *                         producer are not started (used to run the app without a
 *                         broker, e.g. under the {@code test} profile)
 */
@ConfigurationProperties("app.kafka")
public record KafkaProperties(
        String bootstrapServers,
        String clickTopic,
        String consumerGroup,
        boolean enabled) {

    /** Defaults used when the corresponding property is not provided. */
    public KafkaProperties {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "localhost:9092";
        }
        if (clickTopic == null || clickTopic.isBlank()) {
            clickTopic = "url-click-events";
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            consumerGroup = "url-analytics";
        }
    }
}