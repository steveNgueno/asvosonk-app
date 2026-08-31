-- =============================================================
-- ASVOSONK — Module Aides (assistance sociale / solidarité)
--
-- Une aide est débloquée pour un membre lors d'un événement
-- (décès, naissance, mariage). Sa somme est recouvrée peu à peu :
-- à la création de l'aide, une part par membre est figée dans
-- aid_contribution (instantané des membres actifs — un membre qui
-- adhère plus tard n'est pas concerné par l'aide en cours).
--
-- L'aide n'est plus d'actualité (statut completed) lorsque tous
-- les membres ont recouvert leur part.
-- =============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. TYPES ÉNUMÉRÉS
-- ─────────────────────────────────────────────────────────────

-- Montants de référence des statuts ; le trésorier peut les ajuster.
CREATE TYPE aid_type AS ENUM (
    'deces_membre',     -- décès d'un membre        (340 000)
    'deces_conjoint',   -- décès du conjoint        (340 000)
    'deces_parent',     -- décès d'un parent        (200 000)
    'deces_enfant',     -- décès d'un enfant        (200 000)
    'naissance',        -- naissance                (100 000)
    'mariage',          -- mariage                  (250 000)
    'autre'             -- autre aide votée en réunion
);

CREATE TYPE aid_status AS ENUM ('in_progress', 'completed');

CREATE TYPE aid_contribution_status AS ENUM ('owed', 'paid');

-- direct            : le membre verse lui-même sa part ;
-- retained_presence : retenue sur sa tontine de présence ;
-- retained_tontine  : retenue sur sa grande tontine.
CREATE TYPE aid_payment_mode AS ENUM ('direct', 'retained_presence', 'retained_tontine');

-- ─────────────────────────────────────────────────────────────
-- 2. TABLES
-- ─────────────────────────────────────────────────────────────

-- L'aide elle-même : qui, pourquoi, combien, quelle part chacun doit recouvrir.
-- total_amount   : somme remise au membre concerné.
-- share_per_member : part que chaque membre concerné devra verser,
--                    fixée par la réunion elle-même (division et arrondi
--                    faits par l'assemblée, pas par l'application).
-- session_id     : séance au cours de laquelle l'aide a été enregistrée.
CREATE TABLE aid (
    id                 BIGSERIAL PRIMARY KEY,
    beneficiary_id     BIGINT               NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    aid_type           aid_type             NOT NULL DEFAULT 'autre',
    aid_date           DATE                 NOT NULL,
    description        TEXT,
    total_amount       NUMERIC(12,2)        NOT NULL CHECK (total_amount > 0),
    share_per_member   NUMERIC(10,2)        NOT NULL CHECK (share_per_member >= 0),
    status             aid_status           NOT NULL DEFAULT 'in_progress',
    session_id         BIGINT               REFERENCES meeting_session(id) ON DELETE SET NULL,
    created_at         TIMESTAMP            NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP            NOT NULL DEFAULT now()
);

CREATE INDEX idx_aid_beneficiary ON aid(beneficiary_id);
CREATE INDEX idx_aid_status      ON aid(status);
CREATE INDEX idx_aid_date        ON aid(aid_date);

-- La part de chaque membre pour une aide donnée. Instantané à la création :
-- un membre adhérant après coup n'a simplement pas de ligne ici.
-- amount_paid suit les encaissements partiels : une retenue sur tontine peut
-- ne couvrir qu'une partie de la part, comme pour les sanctions.
CREATE TABLE aid_contribution (
    id              BIGSERIAL PRIMARY KEY,
    aid_id          BIGINT                NOT NULL REFERENCES aid(id) ON DELETE CASCADE,
    member_id       BIGINT                NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    amount_due      NUMERIC(10,2)         NOT NULL DEFAULT 0 CHECK (amount_due >= 0),
    amount_paid     NUMERIC(10,2)         NOT NULL DEFAULT 0,
    status          aid_contribution_status NOT NULL DEFAULT 'owed',
    payment_mode    aid_payment_mode,
    payment_date    DATE,
    session_id      BIGINT REFERENCES meeting_session(id) ON DELETE SET NULL,
    created_at      TIMESTAMP             NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP             NOT NULL DEFAULT now(),
    UNIQUE (aid_id, member_id),
    CONSTRAINT chk_aid_contrib_amount_paid_nonneg CHECK (amount_paid >= 0),
    CONSTRAINT chk_aid_contrib_amount_paid_le_due CHECK (amount_paid <= amount_due)
);

CREATE INDEX idx_aid_contribution_aid    ON aid_contribution(aid_id);
CREATE INDEX idx_aid_contribution_member ON aid_contribution(member_id);
CREATE INDEX idx_aid_contribution_session ON aid_contribution(session_id);
CREATE INDEX idx_aid_contribution_open   ON aid_contribution(member_id)
    WHERE status = 'owed';

-- ─────────────────────────────────────────────────────────────
-- 3. RAPPORT DE SÉANCE
-- ─────────────────────────────────────────────────────────────

ALTER TABLE session_report ADD COLUMN presence_aid_deductions NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE session_report ADD COLUMN tontine_aid_deductions  NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Les recouvrements d'aides sont des entrées du jour remises directement
-- au trésorier (ils ne transitent par aucune caisse).
ALTER TABLE session_report ADD COLUMN aids_collected NUMERIC(12,2) NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────
-- 4. PERMISSIONS ET RÔLES
-- ─────────────────────────────────────────────────────────────

INSERT INTO permission (code, description) VALUES
('AID_VIEW',             'View aids and their recovery progress'),
('AID_CREATE',           'Record a new aid'),
('AID_RECORD_RECOVERY',  'Record an aid recovery (direct payment)');

-- Les rôles complets récupèrent les nouvelles permissions…
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name IN ('PRESIDENT', 'SECRETARY', 'TREASURER')
  AND p.code LIKE 'AID_%';

-- … le censeur consulte, l'auditeur aussi.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name IN ('CENSOR', 'AUDITOR')
  AND p.code = 'AID_VIEW';
