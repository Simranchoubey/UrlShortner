package com.example.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link ClientIpResolver}: the X-Forwarded-For first-header
 * convention shared by click analytics and rate limiting (Phase 8/9).
 */
class ClientIpResolverTest {

    @Test
    void fallsBackToRemoteAddrWithoutForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void takesFirstAddressFromForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 198.51.100.2");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void blankForwardedHeaderFallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void trimsWhitespaceFromFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.50 , 198.51.100.2 ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.50");
    }
}