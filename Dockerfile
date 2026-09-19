# Multi-stage build for the URL Shortener service.
#
# Stage 1 (builder): compiles and packages the application with Maven.
# Stage 2 (runtime): minimal JRE image running the repackaged Spring Boot jar.
#
# NOTE: Docker is NOT installed in the authoring environment, so this image has
# not been built or run. It is provided as a Phase 10 starting point and should be
# validated on a real Docker host. Build with:  docker build -t urlshortener .
#
# The container connects to Postgres/Redis/Kafka per docker-compose.yml and all
# secrets are injected via environment variables at runtime (never baked in).

# --- Stage 1: build ---
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# Copy POM first to cache dependency downloads across builds.
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
# Copy sources and package (skip tests for the image build; run them separately).
COPY src ./src
RUN mvn -q -DskipTests package

# --- Stage 2: runtime ---
FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app
WORKDIR /app
COPY --from=builder /build/target/urlshortener-0.0.1-SNAPSHOT.jar app.jar
# Run as a non-root user (defense in depth).
USER appuser
# A Spring Boot fat jar is a valid entrypoint target.
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
# Server port; OpenAPI docs at /v3/api-docs, Swagger UI at /swagger-ui.html.
EXPOSE 8080