-- =============================================================
-- ASVOSONK — Session report persistence
-- Stores financial data computed at session close,
-- so the report page works even after flash attributes expire.
-- =============================================================

CREATE TABLE session_report (
    id                   BIGSERIAL PRIMARY KEY,
    session_id           BIGINT        NOT NULL UNIQUE REFERENCES meeting_session(id) ON DELETE CASCADE,
    gross_tontine        NUMERIC(12,2) NOT NULL DEFAULT 0,
    sanction_deductions  NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_tontine          NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_development    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_beverage_pool  NUMERIC(12,2) NOT NULL DEFAULT 0,
    actual_beverage_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
    beverage_reliquat    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cotisants      INT           NOT NULL DEFAULT 0,
    present_count        INT           NOT NULL DEFAULT 0,
    fund_covered_count   INT           NOT NULL DEFAULT 0,
    default_count        INT           NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_report_session ON session_report(session_id);
