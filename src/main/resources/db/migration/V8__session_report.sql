-- =============================================================
-- ASVOSONK — Session Report v8
--
-- Replaces the old session_report table with a comprehensive
-- schema covering all meeting steps: présence, grande tontine,
-- banque projet, banque annuelle.
-- =============================================================

DROP TABLE IF EXISTS session_report CASCADE;

CREATE TABLE session_report (
    id                              BIGSERIAL PRIMARY KEY,
    session_id                      BIGINT        NOT NULL UNIQUE REFERENCES meeting_session(id) ON DELETE CASCADE,

    -- ── Présence ─────────────────────────────────────────────
    presence_beneficiary_id         BIGINT        REFERENCES member(id),
    presence_total_cotisants        INT           NOT NULL DEFAULT 0,
    presence_present_count          INT           NOT NULL DEFAULT 0,
    presence_fund_covered_count     INT           NOT NULL DEFAULT 0,
    presence_default_count          INT           NOT NULL DEFAULT 0,
    presence_gross_tontine          NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_sanction_deductions    NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_net_tontine            NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_development_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_beverage_reliquat      NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- ── Grande Tontine ───────────────────────────────────────
    tontine_beneficiary_id          BIGINT        REFERENCES member(id),
    tontine_gross_collected         NUMERIC(12,2) NOT NULL DEFAULT 0,
    tontine_sanction_deductions     NUMERIC(12,2) NOT NULL DEFAULT 0,
    tontine_net_paid                NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- ── Banque Projet ────────────────────────────────────────
    banque_projet_collected         NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- ── Banque Annuelle ──────────────────────────────────────
    banque_annuelle_savings         NUMERIC(12,2) NOT NULL DEFAULT 0,
    banque_annuelle_repayments      NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- ── Synthèse ─────────────────────────────────────────────
    total_to_treasurer              NUMERIC(12,2) NOT NULL DEFAULT 0,
    generated_at                    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_report_session ON session_report(session_id);
