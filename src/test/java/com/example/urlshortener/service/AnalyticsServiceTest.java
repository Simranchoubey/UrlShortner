package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalyticsService}: it answers the persisted click count for
 * a URL, returning 0 when none have been recorded.
 */
class AnalyticsServiceTest {

    private final ClickEventRepository clickEventRepository =
            org.mockito.Mockito.mock(ClickEventRepository.class);

    private final AnalyticsService service = new AnalyticsService(clickEventRepository);

    @Test
    void returnsThePersistedClickCountForAUrl() {
        when(clickEventRepository.countByUrlId(123L)).thenReturn(87L);

        assertThat(service.countClicksForUrl(123L)).isEqualTo(87L);
    }

    @Test
    void returnsZeroWhenNoClicksAreRecorded() {
        when(clickEventRepository.countByUrlId(999L)).thenReturn(0L);

        assertThat(service.countClicksForUrl(999L)).isZero();
    }
}