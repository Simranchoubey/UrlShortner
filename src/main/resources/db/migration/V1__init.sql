-- V1__init.sql — Initial schema for the URL Shortener (PostgreSQL).
-- Created in Phase 2. Idempotent structure only; no data seed here.

-- Users: accounts that own URLs.
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Unique index on email (login lookup).
CREATE UNIQUE INDEX ux_users_email ON users (email);

-- URLs: mappings from short_code to the original long URL.
CREATE TABLE urls (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NULL,          -- nullable so links can outlive an owner/anon
    short_code    VARCHAR(12)   NOT NULL,
    original_url  TEXT          NOT NULL,
    custom_alias  VARCHAR(64)   NULL,
    expires_at    TIMESTAMPTZ   NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Foreign key: url belongs to a user. On owner deletion keep the URL but orphan it.
ALTER TABLE urls
    ADD CONSTRAINT fk_urls_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

-- Unique index on short_code — this is the redirect lookup path (see §4.2).
-- A unique constraint already provides a backing index; we name it explicitly
-- so the lookup stays fast even at millions of rows.
CREATE UNIQUE INDEX ux_urls_short_code ON urls (short_code);

-- Unique (nullable) index for custom aliases; NULLs allowed multiple times.
CREATE UNIQUE INDEX ux_urls_custom_alias ON urls (custom_alias);

-- Index for "find all URLs belonging to a user".
CREATE INDEX ix_urls_user_id ON urls (user_id);

-- Click events: one row per redirect, written asynchronously via Kafka (Phase 8).
CREATE TABLE click_events (
    id          BIGSERIAL PRIMARY KEY,
    url_id      BIGINT        NOT NULL,
    short_code  VARCHAR(12)   NOT NULL,
    event_time  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    referrer    VARCHAR(2048) NULL,
    user_agent  TEXT          NULL,
    ip          VARCHAR(45)   NULL,   -- IPv4/IPv6 as text
    country     CHAR(2)       NULL
);

-- Foreign key: click event belongs to a URL.
ALTER TABLE click_events
    ADD CONSTRAINT fk_click_events_url
    FOREIGN KEY (url_id) REFERENCES urls (id) ON DELETE CASCADE;

-- Index for "list analytics for a URL".
CREATE INDEX ix_click_events_url_id ON click_events (url_id);

-- Supporting index for reporting/dedup by the short code that was actually hit.
CREATE INDEX ix_click_events_short_code ON click_events (short_code);