package com.example.urlshortener.messaging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link ClickEventPublisher} used only under the {@code test} profile.
 *
 * <p>Docker/Kafka is not assumed available on developer machines, so instead of a
 * real broker, this double captures published {@link ClickEventMessage}s for
 * assertions while still honouring the best-effort contract of the interface (it
 * never throws, and it records each event). The {@code ClickEventConsumer} /
 * {@code KafkaClickEventPublisher} are excluded from the {@code test} classpath via
 * {@code @Profile("!test")}, so the context loads with no Kafka infrastructure.
 *
 * <p>It also supports simulating a producer failure ({@link #failNextPublish()}) so
 * tests can verify that a redirect still returns 302 even when publishing throws.
 */
@Component
@Profile("test")
public class FakeClickEventPublisher implements ClickEventPublisher {

    private final List<ClickEventMessage> published = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    @Override
    public void publishClick(ClickEventMessage event) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("simulated Kafka publish failure");
        }
        if (event != null) {
            published.add(event);
        }
    }

    /** All events published so far by the redirect flow, in order. */
    public List<ClickEventMessage> publishedEvents() {
        return List.copyOf(published);
    }

    /** Clears captured events (call between tests to isolate state). */
    public void clear() {
        published.clear();
    }

    /** Makes the <em>next</em> publish throw, simulating a broker outage. */
    public void failNextPublish() {
        failNext = true;
    }
}