-- =============================================================
-- ASVOSONK — Schema fix v10
--
-- tontine_debt had a full UNIQUE (tour_id, debtor_id, creditor_id).
-- Once a debt is marked 'repaid', that row keeps occupying the slot,
-- so a later legitimate contribution for the same (contributor →
-- beneficiary) pair in the SAME tour (which inserts a new 'owed' debt)
-- violated the constraint and rolled the whole contribution back.
--
-- The documented intent is "one ACTIVE debt per pair per tour", so we
-- replace the full constraint with a PARTIAL unique index that only
-- applies to rows still 'owed'. Historical 'repaid' rows no longer
-- block new debts.
-- =============================================================

ALTER TABLE tontine_debt
    DROP CONSTRAINT IF EXISTS tontine_debt_tour_id_debtor_id_creditor_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tontine_debt_owed_pair
    ON tontine_debt (tour_id, debtor_id, creditor_id)
    WHERE status = 'owed';
