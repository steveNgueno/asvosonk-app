-- =============================================================
-- ASVOSONK — Schema fix v13  (F-16, F-04)
--
-- F-16  Concurrent balance updates: cashbox / revolving_fund use a
--       read-modify-write in the service (SELECT balance, add/sub in
--       Java, UPDATE). Two overlapping transactions can lose an update.
--       Add an optimistic-lock version column to each; Hibernate's
--       @Version then bumps it on every write and fails the second
--       concurrent writer with an OptimisticLockException instead of
--       silently overwriting.
--
-- F-04  A cashbox balance must never go negative. Add a CHECK as a
--       last-line-of-defence at the DB level (the service also guards
--       it before the write, returning a friendly French message).
--
-- IDEMPOTENT: guarded with IF NOT EXISTS.
-- =============================================================

-- ── F-16: optimistic-lock version columns ──────────────────────────
ALTER TABLE cashbox
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE revolving_fund
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ── F-04: cashbox balance must stay non-negative ────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_cashbox_balance_non_negative'
    ) THEN
        ALTER TABLE cashbox
            ADD CONSTRAINT chk_cashbox_balance_non_negative CHECK (balance >= 0);
    END IF;
END
$$;
