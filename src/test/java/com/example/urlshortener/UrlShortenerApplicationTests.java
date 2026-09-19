package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring application context starts successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

    @Test
    void contextLoads() {
        // If the context starts, this test passes by definition.
    }
}