package com.example.urlshortener.config;

import com.example.urlshortener.ratelimit.RateLimiter;
import com.example.urlshortener.service.RateLimitService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link RateLimitService} bean used by the filter (Phase 9).
 *
 * <p>It composes the active {@link RateLimiter} (Redis in production; an
 * in-memory substitute under the {@code test} profile) with the
 * environment-driven {@link RateLimitProperties}. The properties bean is enabled
 * here so {@code app.rate-limit.*} binds from {@code application.yml} / env vars.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimitService rateLimitService(RateLimiter rateLimiter, RateLimitProperties properties) {
        return new RateLimitService(rateLimiter, properties);
    }
}