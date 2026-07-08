-- =============================================================
-- ASVOSONK — Schema fix v5
--
-- Fixes enum mismatch between Java AttendanceStatus and PostgreSQL attendance_status:
--   Java has: default_status (cannot use 'default' — reserved keyword)
--   DB has:   'default' (missing from Java enum)
-- Solution: Add 'default_status' to the PostgreSQL ENUM
-- =============================================================

ALTER TYPE attendance_status ADD VALUE IF NOT EXISTS 'default_status';
