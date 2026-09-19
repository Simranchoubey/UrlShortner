package com.example.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the URL Shortener modular monolith.
 *
 * <p>Phase 1 intentionally suspended data-source auto-configuration so the
 * application could boot without a live PostgreSQL. From Phase 2 onward the
 * DataSource, Flyway migrations, JPA entities, and repositories are enabled and
 * the application connects to PostgreSQL using environment variables.
 */
@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}