-- =============================================================
-- ASVOSONK — Schema fix v12  (F-27)
--
-- Remove the orphan 'default' label left in two ENUM types.
--
--   attendance_status : V1 created it with 'default'; V5 added
--                       'default_status'; V11 migrated the rows but
--                       could not drop 'default' (PostgreSQL has no
--                       DROP VALUE). The unused 'default' label lingers.
--   payment_status    : same story via V9. 'default' lingers unused.
--
-- Both Java enums (AttendanceStatus / PaymentStatus) no longer know
-- 'default'. Leaving the label in the DB is a latent trap (a stray
-- row could hold a value absent from the Java enum). This migration
-- rebuilds each type WITHOUT 'default'.
--
-- Technique (only way to physically remove an ENUM label < PG 10-agnostic):
--   rename old type -> create clean type -> convert column with a CASE
--   USING clause (folding any residual 'default' into 'default_status')
--   -> drop old type.
--
-- The view global_movement_view casts both columns to TEXT and therefore
-- depends on them; it is dropped up-front and recreated VERBATIM at the
-- end (its logic is intentionally left untouched here — that is F-42,
-- a later wave). The index idx_attendance_status is rebuilt automatically
-- by ALTER COLUMN ... TYPE, so no manual handling is required.
--
-- IDEMPOTENT: each rebuild is guarded on the presence of the 'default'
-- label, so re-running on an already-clean DB is a no-op (the view is
-- simply dropped and recreated identically).
-- =============================================================

-- ── 0. Drop the dependent view (blocks ALTER COLUMN TYPE otherwise) ──
DROP VIEW IF EXISTS global_movement_view;

-- ── 1. Rebuild attendance_status without 'default' ──────────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'attendance_status' AND e.enumlabel = 'default'
    ) THEN
        ALTER TYPE attendance_status RENAME TO attendance_status_old;

        CREATE TYPE attendance_status AS ENUM
            ('up_to_date', 'covered_by_fund', 'default_status', 'recovered');

        ALTER TABLE session_attendance
            ALTER COLUMN attendance_status DROP DEFAULT;

        ALTER TABLE session_attendance
            ALTER COLUMN attendance_status TYPE attendance_status
            USING (CASE attendance_status::text
                       WHEN 'default' THEN 'default_status'
                       ELSE attendance_status::text
                   END)::attendance_status;

        ALTER TABLE session_attendance
            ALTER COLUMN attendance_status SET DEFAULT 'up_to_date';

        DROP TYPE attendance_status_old;
    END IF;
END
$$;

-- ── 2. Rebuild payment_status without 'default' ─────────────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'payment_status' AND e.enumlabel = 'default'
    ) THEN
        ALTER TYPE payment_status RENAME TO payment_status_old;

        CREATE TYPE payment_status AS ENUM ('paid', 'default_status');

        ALTER TABLE tontine_contribution
            ALTER COLUMN status DROP DEFAULT;

        ALTER TABLE tontine_contribution
            ALTER COLUMN status TYPE payment_status
            USING (CASE status::text
                       WHEN 'default' THEN 'default_status'
                       ELSE status::text
                   END)::payment_status;

        ALTER TABLE tontine_contribution
            ALTER COLUMN status SET DEFAULT 'paid';

        DROP TYPE payment_status_old;
    END IF;
END
$$;

-- ── 3. Recreate global_movement_view (verbatim from V1) ─────────────
CREATE VIEW global_movement_view AS
    -- Presence attendances
    SELECT
        'presence'              AS module,
        sa.id                   AS reference_id,
        sa.member_id,
        ms.session_date         AS event_date,
        sa.amount_paid          AS amount,
        sa.attendance_status::TEXT AS status
    FROM session_attendance sa
    JOIN meeting_session ms ON ms.id = sa.session_id

    UNION ALL

    -- Grand tontine contributions
    SELECT
        'grand_tontine',
        tc.id,
        tc.contributor_id       AS member_id,
        ms.session_date,
        tc.amount,
        tc.status::TEXT
    FROM tontine_contribution tc
    JOIN meeting_session ms ON ms.id = tc.session_id

    UNION ALL

    -- Sanctions
    SELECT
        'sanction',
        s.id,
        s.member_id,
        s.sanction_date,
        s.amount,
        s.status::TEXT
    FROM sanction s

    UNION ALL

    -- Cashbox movements
    SELECT
        'cashbox_' || c.type::TEXT,
        cm.id,
        cm.member_id,
        cm.movement_date::DATE,
        cm.amount,
        cm.direction::TEXT
    FROM cashbox_movement cm
    JOIN cashbox c ON c.id = cm.cashbox_id;
