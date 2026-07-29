-- =============================================================
-- ASVOSONK — Schema fix v15  (F-25)
--
-- V1 granted `SELECT ON ALL TABLES` to the read-only reporting role
-- `asvosonk_reports`, which let it read `app_user.password_hash`,
-- `role`, `permission` and `role_permission` — sensitive auth data
-- that reports never need.
--
-- This migration revokes the blanket grant and re-grants SELECT on a
-- WHITELIST of business tables/views only. Auth tables are excluded.
--
-- IDEMPOTENT: REVOKE/GRANT are safe to re-run. Guarded so it is a
-- no-op if the role does not exist (e.g. some test environments).
-- =============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'asvosonk_reports') THEN
        RAISE NOTICE 'Role asvosonk_reports absent — V15 skipped.';
        RETURN;
    END IF;

    -- 1. Remove the over-privileged blanket grants.
    REVOKE SELECT ON ALL TABLES    IN SCHEMA public FROM asvosonk_reports;
    REVOKE SELECT ON ALL SEQUENCES IN SCHEMA public FROM asvosonk_reports;

    -- Stop auto-granting SELECT on future tables (auth tables included).
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        REVOKE SELECT ON TABLES FROM asvosonk_reports;

    -- 2. Whitelist: business tables + the reporting view only.
    --    Excluded on purpose: app_user, role, permission, role_permission.
    GRANT SELECT ON
        cashbox,
        cashbox_movement,
        loan,
        loan_repayment,
        meeting_session,
        member,
        membership_fee,
        presence_tour,
        presence_tour_participant,
        revolving_fund,
        revolving_fund_movement,
        sanction,
        saving,
        session_attendance,
        session_report,
        tontine_contribution,
        tontine_debt,
        tontine_participant,
        tontine_tour,
        global_movement_view
    TO asvosonk_reports;
END
$$;
