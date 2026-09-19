-- V2__fix_click_event_country_type.sql — Align click_events.country with the JPA entity.
--
-- V1 declared country as CHAR(2) (PostgreSQL bpchar / Types#CHAR), but the
-- ClickEvent entity maps it as String via @Column(length = 2), which Hibernate
-- expects to be VARCHAR(2) (character varying(2)). With ddl-auto: validate this
-- mismatch breaks startup.
--
-- The change is a pure in-place CLUSTER-free cast: CHAR(2) -> VARCHAR(2) is
-- lossless (both hold the same 2-letter codes), so existing rows are preserved
-- and the table/column are not dropped.

ALTER TABLE click_events
    ALTER COLUMN country TYPE VARCHAR(2);