package com.example.urlshortener.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation metadata (Phase 10).
 *
 * <p>Exposes the generated spec at {@code /v3/api-docs} and the Swagger UI at
 * {@code /swagger-ui.html}. Rather than applying the JWT security requirement
 * globally (which would wrongly mark the public register/login/redirect/health
 * endpoints as secured), the {@code bearerAuth} {@link SecurityScheme} is
 * declared here and referenced per-controller via
 * {@code @SecurityRequirement(name = "bearerAuth")} on {@code UrlController} —
 * the only controller where every endpoint requires authentication.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .description(
                                "REST API for a URL shortener. Register and log in to get a JWT, then "
                                        + "create, list, and manage your short URLs. Short codes are public: "
                                        + "GET /{shortCode} redirects to the original URL. Async click analytics "
                                        + "are recorded through Kafka and exposed per URL.")
                        .version("1.0.0")
                        .contact(new Contact().name("URL Shortener — Final Year Project")))
                .addServersItem(new Server().url("/").description("Default server URL (use BASE_URL in production)"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "Access token returned by POST /api/v1/auth/login. Send it as "
                                                + "'Authorization: Bearer <token>' on the protected /api/v1/urls endpoints.")));
    }
}