-- F-11 — Protect the financial audit trail from member hard-deletes.
--
-- Every financial FK to member(id) was ON DELETE CASCADE, so deleting one
-- member erased their fees, savings, loans, sanctions AND tontine debts owed
-- to OTHER members — an irreversible loss of the accounting trail.
--
-- These FKs are converted to ON DELETE RESTRICT: the database now refuses to
-- delete a member that still has financial history. "Removing" a member must
-- instead be a status change (resigned / deceased), which the domain already
-- supports. DeleteMemberUseCase enforces the same rule at the application layer
-- with a clear business message.
--
-- Scope: only the true financial-ledger tables are RESTRICTed. These rows only
-- exist once real activity happened, so an empty (mistakenly-added) member with
-- no ledger entries can still be deleted.
--
-- Left intentionally CASCADE / unchanged:
--   * membership_fee, revolving_fund — auto-created for EVERY member at
--     registration (empty fee slots, zero-balance fund). RESTRICTing them would
--     block deleting any member. The paid-amount / positive-balance case is
--     guarded at the application layer (DeleteMemberUseCase), which refuses the
--     delete before it reaches the cascade.
--   * app_user.member_id — already NO ACTION (RESTRICT-like).
--   * meeting_session.beneficiary_id, cashbox_movement.member_id — SET NULL
--     preserves the movement/session row (money trail intact), only detaching
--     the member reference.
--   * session_attendance, presence_tour_participant — operational attendance,
--     not part of the monetary ledger.

DO $$
DECLARE
    -- (table, column) pairs whose FK to member(id) must become RESTRICT
    targets TEXT[][] := ARRAY[
        ['tontine_participant',  'member_id'],
        ['tontine_contribution', 'contributor_id'],
        ['tontine_contribution', 'beneficiary_id'],
        ['tontine_debt',         'debtor_id'],
        ['tontine_debt',         'creditor_id'],
        ['saving',               'member_id'],
        ['loan',                 'member_id'],
        ['sanction',             'member_id']
    ];
    t TEXT;
    c TEXT;
    conname TEXT;
    i INT;
BEGIN
    FOR i IN 1 .. array_length(targets, 1) LOOP
        t := targets[i][1];
        c := targets[i][2];

        -- Find the existing FK constraint on t(c) that references member(id).
        SELECT con.conname INTO conname
        FROM pg_constraint con
        JOIN pg_class rel      ON rel.oid = con.conrelid
        JOIN pg_class ref      ON ref.oid = con.confrelid
        JOIN pg_attribute att  ON att.attrelid = con.conrelid
                              AND att.attnum = con.conkey[1]
        WHERE con.contype = 'f'
          AND rel.relname = t
          AND ref.relname = 'member'
          AND att.attname = c
        LIMIT 1;

        IF conname IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, conname);
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) '
                || 'REFERENCES member(id) ON DELETE RESTRICT', t, conname, c);
        END IF;
    END LOOP;
END $$;
