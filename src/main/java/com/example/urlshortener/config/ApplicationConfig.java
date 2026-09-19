package com.example.urlshortener.config;

import com.example.urlshortener.util.ShortCodeGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for small utility beans used by the URL service.
 *
 * <p>{@link ShortCodeGenerator} is exposed as a bean (rather than built inline)
 * so the URL service can receive it via constructor injection and unit tests can
 * substitute a deterministic fake to exercise collision handling.
 *
 * <p>{@link Clock} is exposed so expiration logic (<em>redirect-time</em> expiry
 * checks) uses an injectable time source — unit tests can pin it to a fixed
 * instant instead of calling {@code Instant.now()} directly (§8 of the plan).
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public ShortCodeGenerator shortCodeGenerator() {
        return new ShortCodeGenerator();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}