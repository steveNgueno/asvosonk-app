-- =============================================================
-- ASVOSONK — Schéma de la base
--
-- Association des Voisins Solidaires de Nkozoa « Nkou-Assi ».
-- Gestion des membres, des séances hebdomadaires (présence, grande
-- tontine, banques), des caisses et des sanctions.
--
-- Ce fichier décrit l'état complet du schéma. Il consolide l'historique
-- des migrations précédentes : toute évolution ultérieure doit faire
-- l'objet d'un nouveau fichier V3, V4, … et jamais d'une modification
-- de celui-ci (Flyway contrôle son empreinte).
--
-- Conventions : noms anglais en snake_case, montants en NUMERIC (jamais
-- en flottant), horodatages sans fuseau, tout dans le schéma `public`.
-- =============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. TYPES ÉNUMÉRÉS
-- ─────────────────────────────────────────────────────────────

CREATE TYPE member_status        AS ENUM ('active', 'suspended', 'resigned', 'deceased');
CREATE TYPE fee_type             AS ENUM ('registration', 'revolving_fund', 'emergency_fund', 'development_fund');
CREATE TYPE session_status       AS ENUM ('planned', 'open', 'closed');

-- default_status : « default » est un mot réservé côté Java ; recovered :
-- échec rattrapé lors d'une séance ultérieure.
CREATE TYPE attendance_status    AS ENUM ('up_to_date', 'covered_by_fund', 'default_status', 'recovered');
CREATE TYPE fund_movement_type   AS ENUM ('advance', 'repayment', 'sanction_fund');

CREATE TYPE tontine_tour_status  AS ENUM ('open', 'closed');
CREATE TYPE payment_status       AS ENUM ('paid', 'default_status');
CREATE TYPE debt_status          AS ENUM ('owed', 'repaid');
CREATE TYPE presence_tour_status AS ENUM ('open', 'closed');

CREATE TYPE loan_status          AS ENUM ('active', 'repaid', 'overdue');

CREATE TYPE cashbox_type         AS ENUM ('development', 'sanction', 'beverage', 'bank');
CREATE TYPE movement_direction   AS ENUM ('in', 'out');
CREATE TYPE movement_origin      AS ENUM ('presence', 'grand_tontine', 'annual_bank', 'sanction', 'manual');

CREATE TYPE sanction_origin      AS ENUM ('presence_fund', 'presence_default', 'tontine_default', 'manual');
CREATE TYPE sanction_status      AS ENUM ('unpaid', 'paid', 'cancelled');

-- ─────────────────────────────────────────────────────────────
-- 2. MEMBRES ET ADHÉSION
-- ─────────────────────────────────────────────────────────────

CREATE TABLE member (
    id          BIGSERIAL PRIMARY KEY,
    full_name   VARCHAR(150)  NOT NULL,
    phone       VARCHAR(30),
    join_date   DATE          NOT NULL,
    is_resident BOOLEAN       NOT NULL DEFAULT true,
    status      member_status NOT NULL DEFAULT 'active',
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_member_status ON member(status);

-- Frais d'adhésion, payables par avances successives.
CREATE TABLE membership_fee (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT        NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    fee_type     fee_type      NOT NULL,
    amount_due   NUMERIC(10,2) NOT NULL,
    amount_paid  NUMERIC(10,2) NOT NULL DEFAULT 0,
    payment_date DATE,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (member_id, fee_type),
    CONSTRAINT chk_membership_fee_due_nonneg  CHECK (amount_due  >= 0),
    CONSTRAINT chk_membership_fee_paid_nonneg CHECK (amount_paid >= 0),
    CONSTRAINT chk_membership_fee_paid_le_due CHECK (amount_paid <= amount_due)
);

-- Le journal des versements (membership_fee_payment) est déclaré plus bas :
-- il référence meeting_session.

-- ─────────────────────────────────────────────────────────────
-- 3. SÉCURITÉ : RÔLES, PERMISSIONS, COMPTES
-- ─────────────────────────────────────────────────────────────

CREATE TABLE role (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE permission (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(60) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE role_permission (
    role_id       INTEGER NOT NULL REFERENCES role(id)       ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Un compte est toujours rattaché à un membre. La suppression d'un membre
-- est refusée tant qu'un compte existe (RESTRICT implicite).
CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    login           VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         INTEGER      NOT NULL REFERENCES role(id),
    member_id       BIGINT       NOT NULL UNIQUE REFERENCES member(id),
    active          BOOLEAN      NOT NULL DEFAULT true,
    last_login      TIMESTAMP,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────
-- 4. SÉANCES
-- ─────────────────────────────────────────────────────────────

-- current_step porte le déroulé réel de la séance (CREATED, PRESENCE_OPEN,
-- …, REPORT_GENERATED) : c'est la source de vérité de l'avancement.
CREATE TABLE meeting_session (
    id                        BIGSERIAL PRIMARY KEY,
    session_date              DATE           NOT NULL UNIQUE,
    status                    session_status NOT NULL DEFAULT 'planned',
    agenda                    TEXT,
    current_step              VARCHAR(30)    NOT NULL DEFAULT 'CREATED',
    presence_beneficiary_id   BIGINT         REFERENCES member(id),
    presence_closed_at        TIMESTAMP,
    tontine_closed_at         TIMESTAMP,
    banque_projet_closed_at   TIMESTAMP,
    banque_annuelle_closed_at TIMESTAMP,
    report_generated_at       TIMESTAMP,
    closed_at                 TIMESTAMP,
    created_by                BIGINT         REFERENCES app_user(id) ON DELETE SET NULL,
    created_at                TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_date   ON meeting_session(session_date);
CREATE INDEX idx_session_status ON meeting_session(status);

-- Une seule séance à la fois peut être en cours : la suivante ne s'ouvre
-- qu'une fois la précédente clôturée (statut « closed », posé à la génération
-- du rapport). L'expression indexée vaut toujours `true` pour les lignes
-- retenues : l'unicité porte donc sur l'ensemble des séances non clôturées.
CREATE UNIQUE INDEX ux_session_single_open
    ON meeting_session ((status <> 'closed'))
    WHERE status <> 'closed';

-- Journal des versements de frais d'adhésion. Un frais se règle par avances
-- successives : chaque versement est daté et rattaché à la séance au cours de
-- laquelle il a été remis — c'est ce lien qui le fait entrer dans les entrées
-- du jour. L'argent ne transite par aucune caisse : il est remis au trésorier.
--
-- Le NOT NULL sur session_id porte la règle « les frais d'adhésion ne se
-- paient qu'en séance ».
CREATE TABLE membership_fee_payment (
    id          BIGSERIAL PRIMARY KEY,
    fee_id      BIGINT        NOT NULL REFERENCES membership_fee(id)  ON DELETE CASCADE,
    session_id  BIGINT        NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    amount      NUMERIC(10,2) NOT NULL,
    recorded_by BIGINT        REFERENCES app_user(id) ON DELETE SET NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_fee_payment_amount_pos CHECK (amount > 0)
);

CREATE INDEX idx_fee_payment_fee     ON membership_fee_payment(fee_id);
CREATE INDEX idx_fee_payment_session ON membership_fee_payment(session_id);

-- Une ligne par membre et par séance.
--   amount_due : 2 000 FCFA, ou 1 000 lorsque le bénéficiaire du jour a
--                rejoint le tour après que ce membre a déjà bénéficié
--                (il ne lui doit alors que boisson + développement).
--   recovered_session_id : séance au cours de laquelle un échec sur CETTE
--                séance a été recouvert ; tant qu'elle est nulle et que le
--                statut vaut default_status, la séance reste due.
CREATE TABLE session_attendance (
    id                   BIGSERIAL PRIMARY KEY,
    session_id           BIGINT            NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    member_id            BIGINT            NOT NULL REFERENCES member(id)          ON DELETE CASCADE,
    is_present           BOOLEAN           NOT NULL DEFAULT false,
    amount_due           NUMERIC(10,2)     NOT NULL DEFAULT 2000,
    amount_paid          NUMERIC(10,2)     NOT NULL DEFAULT 0,
    covered_by_fund      BOOLEAN           NOT NULL DEFAULT false,
    attendance_status    attendance_status NOT NULL DEFAULT 'up_to_date',
    recovered_session_id BIGINT            REFERENCES meeting_session(id) ON DELETE SET NULL,
    created_at           TIMESTAMP         NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP         NOT NULL DEFAULT now(),
    UNIQUE (session_id, member_id),
    CONSTRAINT chk_attendance_amount_paid_nonneg CHECK (amount_paid >= 0)
);

CREATE INDEX idx_attendance_session ON session_attendance(session_id);
CREATE INDEX idx_attendance_member  ON session_attendance(member_id);
CREATE INDEX idx_attendance_status  ON session_attendance(attendance_status);

-- Retrouver rapidement les échecs encore dus d'un membre.
CREATE INDEX idx_attendance_open_failure ON session_attendance(member_id)
    WHERE attendance_status = 'default_status' AND recovered_session_id IS NULL;

-- ─────────────────────────────────────────────────────────────
-- 5. FOND DE ROULEMENT
-- ─────────────────────────────────────────────────────────────

-- 5 000 FCFA par membre, alimentés par le frais d'adhésion du même nom.
-- Le solde ne peut jamais devenir négatif ; `version` protège des mises à
-- jour concurrentes (verrouillage optimiste JPA).
CREATE TABLE revolving_fund (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT        NOT NULL UNIQUE REFERENCES member(id) ON DELETE CASCADE,
    balance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    updated_at TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_revolving_fund_balance_nonneg CHECK (balance >= 0)
);

-- advance   : le fond a cotisé pour le membre (is_recovered passe à true au
--             remboursement) ;
-- repayment : trace d'un versement qui a rechargé le fond.
CREATE TABLE revolving_fund_movement (
    id            BIGSERIAL PRIMARY KEY,
    fund_id       BIGINT             NOT NULL REFERENCES revolving_fund(id)  ON DELETE CASCADE,
    session_id    BIGINT             NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    movement_type fund_movement_type NOT NULL,
    amount        NUMERIC(10,2)      NOT NULL,
    is_recovered  BOOLEAN            NOT NULL DEFAULT false,
    created_at    TIMESTAMP          NOT NULL DEFAULT now(),
    CONSTRAINT chk_fund_movement_amount_nonneg CHECK (amount >= 0)
);

CREATE INDEX idx_fund_movement_fund    ON revolving_fund_movement(fund_id);
CREATE INDEX idx_fund_movement_session ON revolving_fund_movement(session_id);

-- ─────────────────────────────────────────────────────────────
-- 6. TOUR DE PRÉSENCE
-- ─────────────────────────────────────────────────────────────

-- Rotation des bénéficiaires de la tontine de présence. Le bénéficiaire de
-- chaque séance est tiré au sort parmi ceux qui n'ont pas encore bénéficié.
CREATE TABLE presence_tour (
    id         BIGSERIAL PRIMARY KEY,
    start_date DATE                 NOT NULL,
    end_date   DATE,
    status     presence_tour_status NOT NULL DEFAULT 'open',
    created_at TIMESTAMP            NOT NULL DEFAULT now()
);

CREATE INDEX idx_pt_status ON presence_tour(status);

-- joined_mid_tour : membre ayant adhéré après le démarrage du tour. Il ne
-- participe pas aux tirages et bénéficie en dernier ; à son tour, ceux qui
-- avaient déjà bénéficié avant son arrivée ne lui doivent que 1 000 FCFA.
CREATE TABLE presence_tour_participant (
    id              BIGSERIAL PRIMARY KEY,
    tour_id         BIGINT  NOT NULL REFERENCES presence_tour(id)   ON DELETE CASCADE,
    member_id       BIGINT  NOT NULL REFERENCES member(id)          ON DELETE CASCADE,
    draw_order      INTEGER NOT NULL,
    has_benefited   BOOLEAN NOT NULL DEFAULT false,
    session_id      BIGINT  REFERENCES meeting_session(id) ON DELETE SET NULL,
    joined_at       DATE    NOT NULL DEFAULT CURRENT_DATE,
    joined_mid_tour BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tour_id, member_id),
    UNIQUE (tour_id, draw_order)
);

CREATE INDEX idx_pt_participant_tour   ON presence_tour_participant(tour_id);
CREATE INDEX idx_pt_participant_member ON presence_tour_participant(member_id);

-- ─────────────────────────────────────────────────────────────
-- 7. GRANDE TONTINE
-- ─────────────────────────────────────────────────────────────

CREATE TABLE tontine_tour (
    id         BIGSERIAL PRIMARY KEY,
    start_date DATE                NOT NULL,
    end_date   DATE,
    status     tontine_tour_status NOT NULL DEFAULT 'open',
    created_at TIMESTAMP           NOT NULL DEFAULT now()
);

-- Un seul tour ouvert à la fois, y compris en cas de double soumission.
CREATE UNIQUE INDEX ux_tontine_tour_single_open ON tontine_tour(status)
    WHERE status = 'open';

CREATE TABLE tontine_participant (
    id            BIGSERIAL PRIMARY KEY,
    tour_id       BIGINT  NOT NULL REFERENCES tontine_tour(id) ON DELETE CASCADE,
    member_id     BIGINT  NOT NULL REFERENCES member(id)       ON DELETE RESTRICT,
    draw_order    INTEGER NOT NULL,
    has_benefited BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tour_id, member_id),
    UNIQUE (tour_id, draw_order)
);

-- Une cotisation par (tour, séance, cotisant, bénéficiaire) : la contrainte
-- d'unicité neutralise les doubles envois.
CREATE TABLE tontine_contribution (
    id             BIGSERIAL PRIMARY KEY,
    tour_id        BIGINT         NOT NULL REFERENCES tontine_tour(id)    ON DELETE CASCADE,
    session_id     BIGINT         NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    contributor_id BIGINT         NOT NULL REFERENCES member(id)          ON DELETE RESTRICT,
    beneficiary_id BIGINT         NOT NULL REFERENCES member(id)          ON DELETE RESTRICT,
    amount         NUMERIC(10,2)  NOT NULL DEFAULT 0,
    status         payment_status NOT NULL DEFAULT 'paid',
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    UNIQUE (tour_id, session_id, contributor_id, beneficiary_id),
    CONSTRAINT chk_tontine_contrib_amount_nonneg CHECK (amount >= 0)
);

CREATE INDEX idx_contribution_session     ON tontine_contribution(session_id);
CREATE INDEX idx_contribution_contributor ON tontine_contribution(contributor_id);
CREATE INDEX idx_contribution_beneficiary ON tontine_contribution(beneficiary_id);

-- Réciprocité de la tontine à taux variable : le bénéficiaire (debtor) devra
-- rendre au cotisant (creditor) exactement ce qu'il a reçu de lui.
CREATE TABLE tontine_debt (
    id                   BIGSERIAL PRIMARY KEY,
    tour_id              BIGINT        NOT NULL REFERENCES tontine_tour(id)    ON DELETE CASCADE,
    debtor_id            BIGINT        NOT NULL REFERENCES member(id)          ON DELETE RESTRICT,
    creditor_id          BIGINT        NOT NULL REFERENCES member(id)          ON DELETE RESTRICT,
    amount               NUMERIC(10,2) NOT NULL,
    origin_session_id    BIGINT        NOT NULL REFERENCES meeting_session(id),
    status               debt_status   NOT NULL DEFAULT 'owed',
    repayment_session_id BIGINT        REFERENCES meeting_session(id),
    created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_tontine_debt_amount_pos CHECK (amount > 0)
);

CREATE INDEX idx_debt_debtor   ON tontine_debt(debtor_id);
CREATE INDEX idx_debt_creditor ON tontine_debt(creditor_id);
CREATE INDEX idx_debt_status   ON tontine_debt(status);

-- Une seule dette ouverte par couple (tour, débiteur, créancier).
CREATE UNIQUE INDEX ux_tontine_debt_owed_pair
    ON tontine_debt(tour_id, debtor_id, creditor_id)
    WHERE status = 'owed';

-- ─────────────────────────────────────────────────────────────
-- 8. BANQUE ANNUELLE
-- ─────────────────────────────────────────────────────────────

CREATE TABLE saving (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT        NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    operation_date DATE          NOT NULL,
    amount         NUMERIC(10,2) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_saving_amount_pos CHECK (amount > 0)
);

CREATE INDEX idx_saving_member ON saving(member_id);

-- Emprunt à 10 % sur deux mois, adossé à l'épargne du membre.
CREATE TABLE loan (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    loan_date       DATE          NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    interest_rate   NUMERIC(5,2)  NOT NULL DEFAULT 10.00,
    duration_months INTEGER       NOT NULL DEFAULT 2,
    due_date        DATE          NOT NULL,
    total_due       NUMERIC(10,2) NOT NULL,
    status          loan_status   NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_loan_amount_pos    CHECK (amount > 0),
    CONSTRAINT chk_loan_total_due_pos CHECK (total_due > 0)
);

CREATE INDEX idx_loan_member ON loan(member_id);
CREATE INDEX idx_loan_status ON loan(status);

CREATE TABLE loan_repayment (
    id           BIGSERIAL PRIMARY KEY,
    loan_id      BIGINT        NOT NULL REFERENCES loan(id) ON DELETE CASCADE,
    payment_date DATE          NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_loan_repayment_amount_pos CHECK (amount > 0)
);

-- ─────────────────────────────────────────────────────────────
-- 9. CAISSES
-- ─────────────────────────────────────────────────────────────

-- Quatre caisses : développement, sanction, boisson, banque. Le solde ne
-- peut pas devenir négatif ; `version` protège des écritures concurrentes.
CREATE TABLE cashbox (
    id         SERIAL PRIMARY KEY,
    type       cashbox_type  NOT NULL UNIQUE,
    balance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    updated_at TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_cashbox_balance_nonneg CHECK (balance >= 0)
);

-- Journal des entrées et sorties. `session_id` rattache un mouvement à une
-- séance : c'est ce lien qui alimente les entrées et sorties du jour dans
-- le rapport, et donc le montant remis au trésorier.
CREATE TABLE cashbox_movement (
    id            BIGSERIAL PRIMARY KEY,
    cashbox_id    INTEGER            NOT NULL REFERENCES cashbox(id),
    movement_date TIMESTAMP          NOT NULL DEFAULT now(),
    direction     movement_direction NOT NULL,
    amount        NUMERIC(10,2)      NOT NULL,
    reason        TEXT,
    member_id     BIGINT             REFERENCES member(id)          ON DELETE SET NULL,
    session_id    BIGINT             REFERENCES meeting_session(id) ON DELETE SET NULL,
    origin        movement_origin    NOT NULL DEFAULT 'manual',
    reference_id  BIGINT,
    created_by    BIGINT             REFERENCES app_user(id) ON DELETE SET NULL,
    created_at    TIMESTAMP          NOT NULL DEFAULT now(),
    CONSTRAINT chk_cashbox_movement_amount_pos CHECK (amount > 0)
);

CREATE INDEX idx_cm_cashbox ON cashbox_movement(cashbox_id);
CREATE INDEX idx_cm_session ON cashbox_movement(session_id);
CREATE INDEX idx_cm_member  ON cashbox_movement(member_id);
CREATE INDEX idx_cm_date    ON cashbox_movement(movement_date);

-- ─────────────────────────────────────────────────────────────
-- 10. SANCTIONS
-- ─────────────────────────────────────────────────────────────

-- amount_paid suit les encaissements partiels : une retenue sur tontine peut
-- ne couvrir qu'une partie de la sanction, qui reste due pour le solde.
CREATE TABLE sanction (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT          NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    sanction_date DATE            NOT NULL,
    amount        NUMERIC(10,2)   NOT NULL,
    amount_paid   NUMERIC(10,2)   NOT NULL DEFAULT 0,
    reason        TEXT            NOT NULL,
    origin        sanction_origin NOT NULL DEFAULT 'manual',
    reference_id  BIGINT,
    status        sanction_status NOT NULL DEFAULT 'unpaid',
    payment_date  DATE,
    cancel_reason VARCHAR(500),
    created_at    TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP       NOT NULL DEFAULT now(),
    CONSTRAINT chk_sanction_amount_pos  CHECK (amount > 0),
    CONSTRAINT chk_sanction_amount_paid CHECK (amount_paid >= 0 AND amount_paid <= amount)
);

CREATE INDEX idx_sanction_member ON sanction(member_id);
CREATE INDEX idx_sanction_status ON sanction(status);
CREATE INDEX idx_sanction_date   ON sanction(sanction_date);

-- ─────────────────────────────────────────────────────────────
-- 11. RAPPORT DE SÉANCE
-- ─────────────────────────────────────────────────────────────

-- Chiffres figés à la clôture de chaque rubrique.
--
--   total_to_treasurer   : entrées de caisse de la séance moins ses sorties,
--                          plancher à zéro. N'inclut ni les tontines (remises
--                          en main propre) ni le retour dans les fonds de
--                          roulement, qui ne transitent par aucune caisse.
--   total_from_cashboxes : cas inverse — les sorties ont dépassé les entrées,
--                          l'écart a été prélevé sur le solde déjà en caisse.
CREATE TABLE session_report (
    id                           BIGSERIAL PRIMARY KEY,
    session_id                   BIGINT        NOT NULL UNIQUE REFERENCES meeting_session(id) ON DELETE CASCADE,

    -- Présence
    presence_beneficiary_id      BIGINT        REFERENCES member(id),
    presence_total_cotisants     INTEGER       NOT NULL DEFAULT 0,
    presence_present_count       INTEGER       NOT NULL DEFAULT 0,
    presence_fund_covered_count  INTEGER       NOT NULL DEFAULT 0,
    presence_default_count       INTEGER       NOT NULL DEFAULT 0,
    presence_gross_tontine       NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_fund_catch_up       NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_sanction_deductions NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_net_tontine         NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_development_total   NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_beverage_reliquat   NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_return_to_fund      NUMERIC(12,2) NOT NULL DEFAULT 0,
    presence_recovery_total      NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Grande tontine
    tontine_beneficiary_id       BIGINT        REFERENCES member(id),
    tontine_gross_collected      NUMERIC(12,2) NOT NULL DEFAULT 0,
    tontine_sanction_deductions  NUMERIC(12,2) NOT NULL DEFAULT 0,
    tontine_net_paid             NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Banques (saisie non encore implémentée)
    banque_projet_collected      NUMERIC(12,2) NOT NULL DEFAULT 0,
    banque_annuelle_savings      NUMERIC(12,2) NOT NULL DEFAULT 0,
    banque_annuelle_repayments   NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Entrées et sorties de caisse de la séance
    sanctions_collected          NUMERIC(12,2) NOT NULL DEFAULT 0,
    membership_fees_collected    NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_income                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_outflow                NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Synthèse
    total_to_treasurer           NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_from_cashboxes         NUMERIC(12,2) NOT NULL DEFAULT 0,
    generated_at                 TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_report_session ON session_report(session_id);

-- ─────────────────────────────────────────────────────────────
-- 12. VUE TRANSVERSALE
-- ─────────────────────────────────────────────────────────────

-- Alimente la recherche globale : tous les mouvements financiers, quel que
-- soit le module, sous une forme commune.
CREATE VIEW global_movement_view AS
    SELECT 'presence'::text          AS module,
           sa.id                     AS reference_id,
           sa.member_id,
           ms.session_date           AS event_date,
           sa.amount_paid            AS amount,
           sa.attendance_status::text AS status,
           'in'::text                AS direction
      FROM session_attendance sa
      JOIN meeting_session ms ON ms.id = sa.session_id
    UNION ALL
    SELECT 'grand_tontine'::text     AS module,
           tc.id                     AS reference_id,
           tc.contributor_id         AS member_id,
           ms.session_date           AS event_date,
           tc.amount,
           tc.status::text           AS status,
           'in'::text                AS direction
      FROM tontine_contribution tc
      JOIN meeting_session ms ON ms.id = tc.session_id
    UNION ALL
    SELECT 'sanction'::text          AS module,
           s.id                      AS reference_id,
           s.member_id,
           s.sanction_date           AS event_date,
           s.amount,
           s.status::text            AS status,
           'in'::text                AS direction
      FROM sanction s
    UNION ALL
    SELECT 'cashbox_'::text || c.type::text AS module,
           cm.id                     AS reference_id,
           cm.member_id,
           cm.movement_date::date    AS event_date,
           cm.amount,
           cm.direction::text        AS status,
           cm.direction::text        AS direction
      FROM cashbox_movement cm
      JOIN cashbox c ON c.id = cm.cashbox_id;

-- ─────────────────────────────────────────────────────────────
-- 13. RÔLE DE LECTURE POUR LES RAPPORTS
-- ─────────────────────────────────────────────────────────────

-- Le générateur de rapports (module Python) se connecte avec un rôle en
-- lecture seule, restreint aux tables métier : les tables d'authentification
-- (app_user, role, permission, role_permission) lui restent inaccessibles.
--
-- Le mot de passe posé ici n'est qu'une valeur de développement : il doit être
-- changé au déploiement (ALTER ROLE asvosonk_reports PASSWORD '…'). Créer un
-- rôle demande le privilège CREATEROLE ; si le compte de migration ne l'a pas,
-- la création est simplement signalée et le rôle devra être créé à la main.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'asvosonk_reports') THEN
        CREATE ROLE asvosonk_reports LOGIN PASSWORD 'reports_pwd_change_me';
    END IF;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Privilège insuffisant pour créer le rôle asvosonk_reports — à créer manuellement.';
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'asvosonk_reports') THEN
        GRANT USAGE ON SCHEMA public TO asvosonk_reports;
        GRANT SELECT ON
            cashbox, cashbox_movement,
            loan, loan_repayment,
            meeting_session, member, membership_fee, membership_fee_payment,
            presence_tour, presence_tour_participant,
            revolving_fund, revolving_fund_movement,
            sanction, saving,
            session_attendance, session_report,
            tontine_contribution, tontine_debt, tontine_participant, tontine_tour,
            global_movement_view
        TO asvosonk_reports;
    ELSE
        RAISE NOTICE 'Rôle asvosonk_reports absent — attribution des droits ignorée.';
    END IF;
END
$$;
