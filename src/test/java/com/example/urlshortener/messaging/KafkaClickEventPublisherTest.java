package com.example.urlshortener.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for {@link KafkaClickEventPublisher}: it must send the expected
 * {@link ClickEventMessage} to the configured topic, and must swallow transport
 * failures so a Kafka outage never surfaces to the redirect path (±9 of the plan).
 */
class KafkaClickEventPublisherTest {

    private static final String TOPIC = "url-click-events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ClickEventMessage> kafkaTemplate =
            org.mockito.Mockito.mock(KafkaTemplate.class);

    private final KafkaClickEventPublisher publisher =
            new KafkaClickEventPublisher(kafkaTemplate, TOPIC);

    private static ClickEventMessage sample() {
        return new ClickEventMessage(
                7L, "aB72xK", OffsetDateTime.parse("2030-01-01T00:00:00Z"),
                "https://referrer.example/x", "curl/8.0", "203.0.113.9", null);
    }

    @Test
    void sendsTheExpectedEventToTheConfiguredTopic() {
        ClickEventMessage event = sample();

        publisher.publishClick(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(event.shortCode()), eq(event));
    }

    @Test
    void ignoresNullEvents() {
        publisher.publishClick(null);

        org.mockito.Mockito.verify(kafkaTemplate, org.mockito.Mockito.never())
                .send(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishFailureIsSwallowedSoTheRedirectNeverBreaks() {
        // Simulate a broker outage: the template throws on send.
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(kafkaTemplate).send(eq(TOPIC), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());

        // The publisher must swallow the failure (log-and-continue), not rethrow it.
        assertThatCode(() -> publisher.publishClick(sample()))
                .doesNotThrowAnyException();
    }

    @Test
    void carriesNullMetadataThrough() {
        // A redirect with no metadata → the message keeps the nulls (§5).
        ClickEventMessage event = new ClickEventMessage(
                1L, "code0001", OffsetDateTime.now(), null, null, null, null);

        publisher.publishClick(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq("code0001"), eq(event));
        assertThat(event.referrer()).isNull();
        assertThat(event.userAgent()).isNull();
        assertThat(event.ip()).isNull();
        assertThat(event.urlId()).isEqualTo(1L);
    }
}