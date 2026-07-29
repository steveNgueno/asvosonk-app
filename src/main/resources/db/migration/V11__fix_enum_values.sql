-- =============================================================
-- ASVOSONK — Schema fix v11
--
-- 1. Align the attendance_status ENUM with the Java constant
--    AttendanceStatus.default_status. The literal 'default' is a Java
--    reserved keyword, so the DB label 'default' had to become
--    'default_status'. Hibernate (@JdbcTypeCode(NAMED_ENUM)) sends the
--    Java constant name 'default_status' to the DB.
--
-- 2. Add 'cancelled' to the sanction_status ENUM (CancelSanctionUseCase).
--
-- This migration is IDEMPOTENT and safe on every DB state:
--   * fresh DB   : only 'default' exists          -> rename to 'default_status'
--   * patched DB : both labels already exist       -> migrate leftover rows,
--                                                     leave the unused label
--   * done DB    : only 'default_status' exists     -> no-op
-- =============================================================

-- ── 1. Reconcile attendance_status ───────────────────────────────
DO $$
DECLARE
    has_default        boolean;
    has_default_status boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'default'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'attendance_status')
    ) INTO has_default;

    SELECT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'default_status'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'attendance_status')
    ) INTO has_default_status;

    IF has_default AND NOT has_default_status THEN
        -- Fresh DB: simple rename.
        ALTER TYPE attendance_status RENAME VALUE 'default' TO 'default_status';

    ELSIF has_default AND has_default_status THEN
        -- Partially-patched DB: both labels coexist. Migrate any lingering
        -- rows to the canonical label. The old 'default' label cannot be
        -- dropped in place (PostgreSQL has no DROP VALUE), but it is unused.
        EXECUTE 'UPDATE session_attendance
                    SET attendance_status = ''default_status''
                  WHERE attendance_status = ''default''';
    END IF;
END
$$;

-- ── 2. Add 'cancelled' to sanction_status ────────────────────────
ALTER TYPE sanction_status ADD VALUE IF NOT EXISTS 'cancelled';
