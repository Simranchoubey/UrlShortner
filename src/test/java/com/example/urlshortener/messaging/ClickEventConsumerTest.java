package com.example.urlshortener.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ClickEventConsumer}: it must map an incoming
 * {@link ClickEventMessage} onto the persisted {@link ClickEvent} entity (resolving
 * the URL by id or short code), and must handle malformed/unlinkable events without
 * crashing the consumer (§6 / §9).
 */
class ClickEventConsumerTest {

    private ClickEventRepository clickEventRepository;
    private UrlRepository urlRepository;
    private ClickEventConsumer consumer;

    @BeforeEach
    void setUp() {
        clickEventRepository = org.mockito.Mockito.mock(ClickEventRepository.class);
        urlRepository = org.mockito.Mockito.mock(UrlRepository.class);
        consumer = new ClickEventConsumer(clickEventRepository, urlRepository);
    }

    private static Url url(Long id, String shortCode) {
        Url url = new Url();
        org.springframework.test.util.ReflectionTestUtils.setField(url, "id", id);
        url.setShortCode(shortCode);
        return url;
    }

    private static ClickEventMessage message(Long urlId, String shortCode) {
        return new ClickEventMessage(urlId, shortCode,
                OffsetDateTime.parse("2030-01-01T00:00:00Z"),
                "https://ref.example", "agent", "10.0.0.1", null);
    }

    @Test
    void persistsValidEventMappingFieldsOntoClickEvent() {
        Url url = url(9L, "code0001");
        when(urlRepository.findById(9L)).thenReturn(Optional.of(url));

        consumer.onEvent(message(9L, "code0001"));

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        ClickEvent saved = captor.getValue();
        assertThatFieldMapping(saved);
        org.assertj.core.api.Assertions.assertThat(saved.getUrl()).isEqualTo(url);
        org.assertj.core.api.Assertions.assertThat(saved.getEventTime())
                .isEqualTo(OffsetDateTime.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void resolvesUrlByShortCodeWhenUrlIdIsAbsent() {
        // Cache-hit redirects publish without urlId; the consumer resolves it.
        Url url = url(10L, "codeXyz");
        when(urlRepository.findByShortCode("codeXyz")).thenReturn(Optional.of(url));

        consumer.onEvent(new ClickEventMessage(null, "codeXyz",
                OffsetDateTime.now(), null, null, null, null));

        verify(clickEventRepository).save(any(ClickEvent.class));
    }

    @Test
    void dropsEventWhenUrlCannotBeResolved() {
        when(urlRepository.findById(999L)).thenReturn(Optional.empty());

        consumer.onEvent(message(999L, "missing"));

        verify(clickEventRepository, never()).save(any());
    }

    @Test
    void ignoresNullEvents() {
        consumer.onEvent(null);

        verify(clickEventRepository, never()).save(any());
    }

    @Test
    void persistenceFailureIsHandledWithoutCrashing() {
        Url url = url(9L, "code0001");
        when(urlRepository.findById(9L)).thenReturn(Optional.of(url));
        when(clickEventRepository.save(any(ClickEvent.class)))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThatCode(() -> consumer.onEvent(message(9L, "code0001")))
                .doesNotThrowAnyException();
    }

    private static void assertThatFieldMapping(ClickEvent saved) {
        org.assertj.core.api.Assertions.assertThat(saved.getShortCode()).isEqualTo("code0001");
        org.assertj.core.api.Assertions.assertThat(saved.getReferrer()).isEqualTo("https://ref.example");
        org.assertj.core.api.Assertions.assertThat(saved.getUserAgent()).isEqualTo("agent");
        org.assertj.core.api.Assertions.assertThat(saved.getIp()).isEqualTo("10.0.0.1");
        org.assertj.core.api.Assertions.assertThat(saved.getCountry()).isNull();
    }
}