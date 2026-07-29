-- TICKET-SESSION-01 — Remove the Banque Projet and Banque Annuelle steps from
-- the session workflow. They were empty placeholders that blocked progression
-- to the report; the workflow now goes TONTINE_CLOSED -> REPORT_GENERATED.
--
-- NOTE: the ticket named this file V9, but V9..V16 already exist in this repo.
-- Flyway versions must be unique and increasing, so this migration is V17.

-- Move any session stuck on a removed step back to TONTINE_CLOSED BEFORE
-- dropping the columns (current_step is a VARCHAR(30), not an enum type).
UPDATE meeting_session
SET current_step = 'TONTINE_CLOSED'
WHERE current_step IN (
    'BANQUE_PROJET_OPEN', 'BANQUE_PROJET_CLOSED',
    'BANQUE_ANNUELLE_OPEN', 'BANQUE_ANNUELLE_CLOSED'
);

-- Drop the workflow timestamp columns.
ALTER TABLE meeting_session
    DROP COLUMN IF EXISTS banque_projet_closed_at,
    DROP COLUMN IF EXISTS banque_annuelle_closed_at;

-- Drop the (unused) banque report columns.
ALTER TABLE session_report
    DROP COLUMN IF EXISTS banque_projet_collected,
    DROP COLUMN IF EXISTS banque_annuelle_savings,
    DROP COLUMN IF EXISTS banque_annuelle_repayments;
