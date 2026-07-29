-- =============================================================
-- ASVOSONK — Schema fix v14  (F-03)
--
-- The revolving fund must start EMPTY. V1 created it with
-- DEFAULT 5000.00 and members were seeded/created at 5000, i.e.
-- fictitious capital never actually paid in. A member who never
-- paid could still have their session "covered by fund" — money
-- created out of nothing.
--
-- Decision (bureau-validated): the fund starts at 0 and is fed by
-- the real payment of the 'revolving_fund' membership fee (wired in
-- RecordFeePaymentUseCase). Existing balances are reset to 0.
--
-- IDEMPOTENT: setting the default and zeroing balances is safe to
-- re-run.
-- =============================================================

-- New members: no fictitious starting capital.
ALTER TABLE revolving_fund ALTER COLUMN balance SET DEFAULT 0;

-- Existing balances: wipe the fictitious 5 000 capital.
-- (Members re-feed their fund by paying the 'revolving_fund' fee.)
UPDATE revolving_fund SET balance = 0 WHERE balance <> 0;
