-- =============================================================
-- ASVOSONK — Session Workflow v7
--
-- Adds the sequential step tracking columns to meeting_session.
-- =============================================================

ALTER TABLE meeting_session
    ADD COLUMN current_step         VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    ADD COLUMN presence_beneficiary_id BIGINT REFERENCES member(id),
    ADD COLUMN presence_closed_at   TIMESTAMP,
    ADD COLUMN tontine_closed_at    TIMESTAMP,
    ADD COLUMN banque_projet_closed_at    TIMESTAMP,
    ADD COLUMN banque_annuelle_closed_at  TIMESTAMP,
    ADD COLUMN report_generated_at TIMESTAMP;

ALTER TABLE meeting_session DROP COLUMN IF EXISTS beneficiary_id;
