-- =============================================================
-- ASVOSONK — Schema fix v3
--
-- Fixes enum mismatches between JPA entities and PostgreSQL types:
--   1. Add 'cancelled' to sanction_status ENUM (needed by SanctionStatus.java)
-- =============================================================

-- ── 1. Add 'cancelled' to sanction_status ENUM ──────────────
-- The Java enum SanctionStatus has values: unpaid, paid, cancelled
-- The DB ENUM was missing 'cancelled'
ALTER TYPE sanction_status ADD VALUE IF NOT EXISTS 'cancelled';
