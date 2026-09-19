# URL Shortener

A production-oriented URL shortener built with **Java 21 LTS, Spring Boot 3.5.16**,
**PostgreSQL**, **Redis**, **Kafka**, and **JWT** authentication. The project was
developed incrementally across 10 phases (see `IMPLEMENTATION_PLAN.md`), with a
final testing, containerisation, and documentation pass in Phase 10.

> Runtime contract: this repository intentionally targets **Java 21 LTS** with the
> current **Spring Boot 3.5.x** line. The build and Docker runtime now match this
> contract; Java 25 is not required for the current implementation.

## Overview

The service lets a user:

- **Register and log in** — passwords are BCrypt-hashed; authentication issues a
  short-lived **JWT** access token.
- **Create short URLs** — optionally with a custom alias and an expiry timestamp;
  short codes are guaranteed unique.
- **Redirect** on `GET /{shortCode}` — cache-first (Redis) with a PostgreSQL
  fallback; expired links return `410`, unknown codes return `404`.
- **Manage their own URLs** — list (paginated), fetch by id, and delete; a URL
  owned by another user is deliberately indistinguishable from a missing one
  (`404`).
- **View analytics** — an asynchronous click count, written via Kafka.
- **Enforce rate limits** — fixed-window limits in Redis return `429` on abuse.

The application is **stateless** and horizontally scalable: all state lives in
PostgreSQL (source of truth), Redis (cache + rate-limit counters), and Kafka
(async analytics).

## Architecture

```
                     ┌──────────────────────────────┐
   Client ─────────▶ │  Spring Boot (stateless)     │
                     │  - Auth (JWT)                │
                     │  - URL CRUD + Redirect       │
                     │  - Rate limiting             │
                     └──────┬───────────┬───────────┘
                            │           │
                     cache──┘           └── async click events
                            ▼                       ▼
                        ┌────────┐             ┌─────────┐
                        │ Redis  │             │ Kafka   │
                        └────────┘             └─────────┘
                             │                     │
                             ▼                     ▼
                        ┌──────────────────────────────┐
                        │  PostgreSQL (source of truth)│
                        └──────────────────────────────┘
```

- **Synchronous path:** auth, URL creation/management, and redirects (Redis →
  PostgreSQL).
- **Asynchronous path:** analytics (Kafka → consumer → PostgreSQL), keeping
  redirects fast. The redirect is *fire-and-forget* — a Kafka outage never delays
  a `302`.

## Prerequisites

- **Java 21 LTS** (JDK) and **Maven 3.9+** for building/running locally and running tests.
- **Docker** (with Compose) for the one-command stack — **required for the full
  Postgres/Redis/Kafka environment**.

> **Important validation note (Phase 10):** Docker was **not installed** in the
> authoring environment, so `docker-compose.yml` and the multi-stage `Dockerfile`
> were authored but **not executed or verified**. Please validate them on a real
> Docker host before relying on them.

## Running locally (tests only, no Docker)

The test suite runs against **H2** and in-memory Redis/Kafka fakes under the
`test` profile — no external services required:

```bash
mvn test           # or mvn clean verify for the full build + repackaged JAR
```

## Running the full stack (with Docker)

Start the infrastructure (PostgreSQL, Redis, Kafka in KRaft mode):

```bash
docker compose up -d
```

Wait for the three services to report healthy (Compose wires healthchecks), then
build and run the application image:

```bash
docker build -t urlshortener .
docker run --rm --network finalyear_default -p 8080:8080 \
  -e DB_HOST=urlshortener-db \
  -e DB_USERNAME=urlshortener \
  -e DB_PASSWORD=change-me \
  -e REDIS_HOST=urlshortener-redis \
  -e KAFKA_BOOTSTRAP_SERVERS=urlshortener-kafka:9092 \
  -e JWT_SECRET=<a-long-32+-byte-random-secret> \
  urlshortener
```

Or run the app directly on the host against the Compose services (set the same
`DB_HOST`/`REDIS_HOST`/`KAFKA_BOOTSTRAP_SERVERS` to `localhost`).

## Configuration

All configuration is environment-driven — **no hardcoded secrets**. Copy
`.env.example` to `.env` and set real values (`.env` is git-ignored and must
**never** be committed). Key variables:

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8080` |
| `BASE_URL` | Public origin used to build short URLs | `http://localhost:8080` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL connection | `localhost` / `5432` / `urlshortener` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials | empty — **set these** |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection | `localhost` / `6379` |
| `REDIS_PASSWORD` | Redis password (empty = none) | empty |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker | `localhost:9092` |
| `APP_KAFKA_ENABLED` | Enable/disable Kafka listener | `true` |
| `JWT_SECRET` | HS256 signing secret (≥ 32 bytes; app fails to start if too short) | dev-only placeholder |
| `JWT_EXPIRATION_MINUTES` | Access-token lifetime | `15` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | empty (no CORS) |
| `APP_RATE_LIMIT_ENABLED` | Enable rate limiting | `true` |
| `APP_RATE_LIMIT_WINDOW` | Fixed-window length (ISO-8601, e.g. `PT60S`) | `PT60S` |
| `APP_RATE_LIMIT_CREATE_URL` | Create—URL limit per window | `30` |
| `APP_RATE_LIMIT_LOGIN` | Login limit per window | `10` |
| `APP_RATE_LIMIT_REGISTER` | Register limit per window | `5` |
## API

Interactive documentation is available when the app is running:

- Swagger UI → `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON → `http://localhost:8080/v3/api-docs`

These routes are **public** (no JWT). The `bearerAuth` HTTP security scheme is
declared and applied **only** to the `/api/v1/urls` operations — public
auth/redirect/health endpoints are correctly marked as unsecured in the spec.

### Auth (`/api/v1/auth`) — public

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | None | `201` on success; `400` invalid; `409` email taken |
| `POST` | `/api/v1/auth/login` | None | `200` + JWT; `401` generic failure; `429` rate-limited |

**Register request**
```json
{ "email": "user@example.com", "password": "a-strong-password" }
```
`201` → `{ "id": 1, "email": "user@example.com" }` (password hash is never
exposed).

**Login request**
```json
{ "email": "user@example.com", "password": "a-strong-password" }
```
`200` → `{ "accessToken": "<jwt>", "tokenType": "Bearer" }`.

### URLs (`/api/v1/urls`) — JWT required

Send the token as `Authorization: Bearer <jwt>` on every request.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/urls` | Create a short URL |
| `GET` | `/api/v1/urls?page=0&size=20` | List the caller's URLs (newest-first, paginated; `size` clamped to 100) |
| `GET` | `/api/v1/urls/{id}` | Get one of the caller's URLs |
| `DELETE` | `/api/v1/urls/{id}` | Delete one of the caller's URLs (`204`) |
| `GET` | `/api/v1/urls/{id}/analytics` | Click analytics for the caller's URL |

**Create request**
```json
{
  "originalUrl": "https://example.com/some/long/path",
  "customAlias": "my-link",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```
`customAlias` is optional (URL-safe, 3–12 chars); `expiresAt` is optional and must
be in the future. `201` →
```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://example.com/some/long/path",
  "customAlias": "my-link",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```
Errors: `400` invalid input, `401` missing/bad token, `409` duplicate alias,
`429` per-user create limit.

**Analytics response** (`GET /api/v1/urls/{id}/analytics`):
```json
{ "totalClicks": 42 }
```
`totalClicks` is the persisted count (written asynchronously via Kafka) — `0`
until the first click is recorded.

### Redirect (`GET /{shortCode}`) — public

```bash
curl -i http://localhost:8080/my-link
```
- `302 Found` with the original URL in the `Location` header.
- `404` unknown short code.
- `410 Gone` if the short URL has expired.
- `429` if redirect rate limiting is enabled (off by default).
## Security model

- **Passwords:** BCrypt-hashed; the hash is never returned. Plaintext is never
  logged.
- **Tokens:** short-lived HS256 JWTs signed with `JWT_SECRET`. The app refuses to
  start if the secret is missing or < 32 bytes.
- **Authorization:** every `/api/v1/urls` operation scopes results to the
  authenticated user id from the JWT. Another user's URL is treated as a `404`
  (never enumerated).
- **Login errors are generic:** a `401` never reveals whether the email or
  password was wrong.
- **SSRF:** the redirect merely echoes the destination in the `Location` header;
  the server makes no outbound request to it.
- **CSRF/CORS:** stateless (no session CSRF); CORS origins are configurable and
  empty by default.
- **Secret hygiene:** all secrets come from environment variables; nothing
  sensitive is committed.

## Redirect flow (cache-first)

1. `GET /{shortCode}` hits `UrlService.resolveShortCode`.
2. Read from Redis (`url:{shortCode}`); on cache hit, an unexpired value answers
   immediately.
3. On a miss, fall back to PostgreSQL. A valid row is warmed into the cache (with
   TTL bounded by the row's expiry if any).
4. Invalidation removes the key on delete/update.
5. Click metadata (referer, user-agent, client IP) is published to Kafka
   fire-and-forget — the `302` never waits on it.

## Analytics (async)

- The redirect publishes a click event to the Kafka `url-click-events` topic.
- A Spring Kafka consumer aggregates counts into the click-tracking table in
  PostgreSQL.
- `GET /api/v1/urls/{id}/analytics` reads the count back at request time
  (PostgreSQL is the source of truth).
- A Kafka outage never blocks a redirect; it only lags analytics.

## Rate limiting

- Fixed-window counters stored in Redis under `rate-limit:{scope}:{identifier}`
  (a distinct namespace from the URL cache keys).
- Separate limits for register, login, and create-URL (`429` when exceeded).
- Redirect-path limiting is **off by default** (it is the performance-critical
  path).
- **Fail-open:** Redis failures are swallowed and requests are allowed — an outage
  never causes a `500`.
## Testing

Run the full suite (unit + integration, against H2 and in-memory fakes — no
Docker required):

```bash
mvn test
```

Or with verification + packaged JAR:

```bash
mvn clean verify
```

The suite covers auth, URL management, redirect/expiry, caching, analytics/rate
limiting, security, the health endpoint, and the **OpenAPI documentation**
(`OpenApiDocumentationTests` verifies the spec is public, documents all endpoints,
declares the `bearerAuth` scheme on protected routes only, and exposes all request,
response, and error schemas).

## Project layout

```
src/main/java/com/example/urlshortener/
  config/       Spring/OpenAPI/Kafka/property configuration
  controller/   REST + redirect controllers
  dto/          request/response records (never expose entities directly)
  exception/    ApiError + global exception handler
  security/     JWT config and Spring Security filter chain
  service/      business logic
src/main/resources/
  application.yml           environment-driven configuration
  db/migration/             Flyway migrations
docker-compose.yml          Postgres + Redis + Kafka (KRaft) — not yet verified
Dockerfile                  multi-stage app image — not yet verified
IMPLEMENTATION_PLAN.md      10-phase plan + Definition of Done
```

## Status of the Definition of Done

Everything in `IMPLEMENTATION_PLAN.md` §21 is complete **except**:

- **Docker Compose runs the whole stack** — config is provided but **not
  verified** (Docker not installed in the authoring environment).
- **Requests carry IDs in structured logs** — noted as a future/optional
  observability item, not yet implemented.

Swagger/OpenAPI documentation is complete and **verified by
`OpenApiDocumentationTests`**.
| `APP_RATE_LIMIT_REDIRECT_ENABLED` | Redirect-path limiting (off by default) | `false` |