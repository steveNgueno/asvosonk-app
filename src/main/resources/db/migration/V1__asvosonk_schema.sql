-- =============================================================
-- ASVOSONK — Database schema v1
-- PostgreSQL 15+
-- Convention: all names in English, snake_case
-- =============================================================


CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid() if needed

CREATE TABLE permission (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE, -- e.g. MEMBER_CREATE, SESSION_CLOSE
    description TEXT
);

CREATE TABLE role (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE, -- PRESIDENT, SECRETARY, TREASURER, AUDITOR, CENSOR
    description TEXT
);

CREATE TABLE role_permission (
    role_id       INT NOT NULL REFERENCES role(id)       ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);



CREATE TYPE member_status AS ENUM ('active', 'suspended', 'resigned', 'deceased');

CREATE TABLE member (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    join_date       DATE         NOT NULL,
    is_resident     BOOLEAN      NOT NULL DEFAULT true,
    status          member_status NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
                          id                  BIGSERIAL PRIMARY KEY,
                          login               VARCHAR(50)  NOT NULL UNIQUE,
                          password_hash       VARCHAR(255) NOT NULL,          -- BCrypt
                          role_id             INT          NOT NULL REFERENCES role(id),
                          member_id           BIGINT       NOT NULL UNIQUE REFERENCES member(id),
                          active              BOOLEAN      NOT NULL DEFAULT true,
                          last_login          TIMESTAMP,
                          failed_attempts     INT          NOT NULL DEFAULT 0,
                          locked_until        TIMESTAMP,                      -- brute-force protection
                          created_at          TIMESTAMP    NOT NULL DEFAULT now(),
                          updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);


CREATE TYPE fee_type AS ENUM (
    'registration',         -- 2 500 FCFA
    'revolving_fund',       -- 5 000 FCFA
    'emergency_fund',       -- 20 000 FCFA
    'development_fund'      -- 12 500 FCFA
);

CREATE TABLE membership_fee (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    fee_type        fee_type     NOT NULL,
    amount_due      NUMERIC(10,2) NOT NULL,
    amount_paid     NUMERIC(10,2) NOT NULL DEFAULT 0,
    payment_date    DATE,                               -- date fully settled, null if partial
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (member_id, fee_type)
);


CREATE TYPE session_status AS ENUM ('planned', 'open', 'closed');

CREATE TABLE meeting_session (
    id              BIGSERIAL PRIMARY KEY,
    session_date    DATE         NOT NULL UNIQUE,
    status          session_status NOT NULL DEFAULT 'planned',
    agenda          TEXT,
    beneficiary_id  BIGINT       REFERENCES member(id) ON DELETE SET NULL, -- presence tontine winner (manually set)
    closed_at       TIMESTAMP,
    created_by      BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TYPE attendance_status AS ENUM ('up_to_date', 'covered_by_fund', 'default', 'recovered');

CREATE TABLE session_attendance (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT       NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    member_id               BIGINT       NOT NULL REFERENCES member(id)          ON DELETE CASCADE,
    is_present              BOOLEAN      NOT NULL DEFAULT false,
    amount_paid             NUMERIC(10,2) NOT NULL DEFAULT 0,
    covered_by_fund         BOOLEAN      NOT NULL DEFAULT false,
    attendance_status       attendance_status NOT NULL DEFAULT 'up_to_date',
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (session_id, member_id)
);

-- Revolving fund per member (initial balance: 5 000 FCFA)
CREATE TABLE revolving_fund (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL UNIQUE REFERENCES member(id) ON DELETE CASCADE,
    balance     NUMERIC(10,2) NOT NULL DEFAULT 5000.00,
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TYPE fund_movement_type AS ENUM ('advance', 'repayment', 'sanction_fund');

CREATE TABLE revolving_fund_movement (
    id              BIGSERIAL PRIMARY KEY,
    fund_id         BIGINT       NOT NULL REFERENCES revolving_fund(id) ON DELETE CASCADE,
    session_id      BIGINT       NOT NULL REFERENCES meeting_session(id) ON DELETE CASCADE,
    movement_type   fund_movement_type NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    is_recovered    BOOLEAN      NOT NULL DEFAULT false,  -- true when a default is retroactively covered
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);


CREATE TYPE tontine_tour_status AS ENUM ('open', 'closed');

CREATE TABLE tontine_tour (
    id          BIGSERIAL PRIMARY KEY,
    start_date  DATE         NOT NULL,
    end_date    DATE,
    status      tontine_tour_status NOT NULL DEFAULT 'open',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE tontine_participant (
    id              BIGSERIAL PRIMARY KEY,
    tour_id         BIGINT   NOT NULL REFERENCES tontine_tour(id)  ON DELETE CASCADE,
    member_id       BIGINT   NOT NULL REFERENCES member(id)         ON DELETE CASCADE,
    draw_order      INT      NOT NULL,                              -- order set at tour start, immutable
    has_benefited   BOOLEAN  NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tour_id, member_id),
    UNIQUE (tour_id, draw_order)
);

CREATE TYPE payment_status AS ENUM ('paid', 'default');

CREATE TABLE tontine_contribution (
    id              BIGSERIAL PRIMARY KEY,
    tour_id         BIGINT        NOT NULL REFERENCES tontine_tour(id)     ON DELETE CASCADE,
    session_id      BIGINT        NOT NULL REFERENCES meeting_session(id)   ON DELETE CASCADE,
    contributor_id  BIGINT        NOT NULL REFERENCES member(id)            ON DELETE CASCADE,
    beneficiary_id  BIGINT        NOT NULL REFERENCES member(id)            ON DELETE CASCADE,
    amount          NUMERIC(10,2) NOT NULL DEFAULT 0,               -- multiple of 5 000, min 5 000
    status          payment_status NOT NULL DEFAULT 'paid',
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (tour_id, session_id, contributor_id, beneficiary_id)
);

-- Peer-to-peer debt table (core of the grand tontine mechanics)
CREATE TYPE debt_status AS ENUM ('owed', 'repaid');

CREATE TABLE tontine_debt (
    id                      BIGSERIAL PRIMARY KEY,
    tour_id                 BIGINT        NOT NULL REFERENCES tontine_tour(id)     ON DELETE CASCADE,
    debtor_id               BIGINT        NOT NULL REFERENCES member(id)            ON DELETE CASCADE, -- owes the amount (received it as beneficiary)
    creditor_id             BIGINT        NOT NULL REFERENCES member(id)            ON DELETE CASCADE, -- gave the amount (will receive it back)
    amount                  NUMERIC(10,2) NOT NULL,
    origin_session_id       BIGINT        NOT NULL REFERENCES meeting_session(id),  -- session when debt was created
    status                  debt_status   NOT NULL DEFAULT 'owed',
    repayment_session_id    BIGINT        REFERENCES meeting_session(id),           -- null until repaid
    created_at              TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (tour_id, debtor_id, creditor_id)
    -- one active debt per pair per tour; repaid = a new debt may be created in a new tour
);

CREATE TABLE saving (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    operation_date  DATE          NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TYPE loan_status AS ENUM ('active', 'repaid', 'overdue');

CREATE TABLE loan (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    loan_date       DATE          NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    interest_rate   NUMERIC(5,2)  NOT NULL DEFAULT 10.00,           -- 10%
    duration_months INT           NOT NULL DEFAULT 2,
    due_date        DATE          NOT NULL,                         -- loan_date + 2 months
    total_due       NUMERIC(10,2) NOT NULL,                         -- amount + (amount * interest_rate / 100)
    status          loan_status   NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE loan_repayment (
    id              BIGSERIAL PRIMARY KEY,
    loan_id         BIGINT        NOT NULL REFERENCES loan(id) ON DELETE CASCADE,
    payment_date    DATE          NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TYPE cashbox_type AS ENUM ('development', 'sanction', 'beverage', 'bank');

CREATE TABLE cashbox (
    id          SERIAL PRIMARY KEY,
    type        cashbox_type  NOT NULL UNIQUE,
    balance     NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TYPE movement_direction AS ENUM ('in', 'out');
CREATE TYPE movement_origin AS ENUM ('presence', 'grand_tontine', 'annual_bank', 'sanction', 'manual');

CREATE TABLE cashbox_movement (
    id              BIGSERIAL PRIMARY KEY,
    cashbox_id      INT           NOT NULL REFERENCES cashbox(id),
    movement_date   TIMESTAMP     NOT NULL DEFAULT now(),
    direction       movement_direction NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    reason          TEXT,
    member_id       BIGINT        REFERENCES member(id) ON DELETE SET NULL,
    session_id      BIGINT        REFERENCES meeting_session(id) ON DELETE SET NULL,
    origin          movement_origin NOT NULL DEFAULT 'manual',
    reference_id    BIGINT,                                         -- id of the source record (attendance, contribution, sanction…)
    created_by      BIGINT        REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TYPE sanction_origin AS ENUM ('presence_fund', 'presence_default', 'tontine_default', 'manual');
CREATE TYPE sanction_status AS ENUM ('unpaid', 'paid');

CREATE TABLE sanction (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    sanction_date   DATE          NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    reason          TEXT          NOT NULL,
    origin          sanction_origin NOT NULL DEFAULT 'manual',
    reference_id    BIGINT,                                         -- id of the attendance or contribution that triggered it
    status          sanction_status NOT NULL DEFAULT 'unpaid',
    payment_date    DATE,
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now()
);

-- Members
CREATE INDEX idx_member_status            ON member(status);

-- Sessions
CREATE INDEX idx_session_date             ON meeting_session(session_date);
CREATE INDEX idx_session_status           ON meeting_session(status);

-- Attendance
CREATE INDEX idx_attendance_member        ON session_attendance(member_id);
CREATE INDEX idx_attendance_session       ON session_attendance(session_id);
CREATE INDEX idx_attendance_status        ON session_attendance(attendance_status);

-- Revolving fund
CREATE INDEX idx_fund_movement_session    ON revolving_fund_movement(session_id);
CREATE INDEX idx_fund_movement_fund       ON revolving_fund_movement(fund_id);

-- Grand tontine
CREATE INDEX idx_contribution_contributor ON tontine_contribution(contributor_id);
CREATE INDEX idx_contribution_beneficiary ON tontine_contribution(beneficiary_id);
CREATE INDEX idx_contribution_session     ON tontine_contribution(session_id);
CREATE INDEX idx_debt_debtor              ON tontine_debt(debtor_id);
CREATE INDEX idx_debt_creditor            ON tontine_debt(creditor_id);
CREATE INDEX idx_debt_status              ON tontine_debt(status);

-- Annual bank
CREATE INDEX idx_saving_member            ON saving(member_id);
CREATE INDEX idx_loan_member              ON loan(member_id);
CREATE INDEX idx_loan_status              ON loan(status);

-- Sanctions
CREATE INDEX idx_sanction_member          ON sanction(member_id);
CREATE INDEX idx_sanction_status          ON sanction(status);
CREATE INDEX idx_sanction_date            ON sanction(sanction_date);

-- Cashbox movements
CREATE INDEX idx_cm_cashbox               ON cashbox_movement(cashbox_id);
CREATE INDEX idx_cm_member                ON cashbox_movement(member_id);
CREATE INDEX idx_cm_session               ON cashbox_movement(session_id);
CREATE INDEX idx_cm_date                  ON cashbox_movement(movement_date);

CREATE VIEW global_movement_view AS
    -- Presence attendances
    SELECT
        'presence'              AS module,
        sa.id                   AS reference_id,
        sa.member_id,
        ms.session_date         AS event_date,
        sa.amount_paid          AS amount,
        sa.attendance_status::TEXT AS status
    FROM session_attendance sa
    JOIN meeting_session ms ON ms.id = sa.session_id

    UNION ALL

    -- Grand tontine contributions
    SELECT
        'grand_tontine',
        tc.id,
        tc.contributor_id       AS member_id,
        ms.session_date,
        tc.amount,
        tc.status::TEXT
    FROM tontine_contribution tc
    JOIN meeting_session ms ON ms.id = tc.session_id

    UNION ALL

    -- Sanctions
    SELECT
        'sanction',
        s.id,
        s.member_id,
        s.sanction_date,
        s.amount,
        s.status::TEXT
    FROM sanction s

    UNION ALL

    -- Cashbox movements
    SELECT
        'cashbox_' || c.type::TEXT,
        cm.id,
        cm.member_id,
        cm.movement_date::DATE,
        cm.amount,
        cm.direction::TEXT
    FROM cashbox_movement cm
    JOIN cashbox c ON c.id = cm.cashbox_id;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'asvosonk_reports') THEN
        CREATE ROLE asvosonk_reports LOGIN PASSWORD 'reports_pwd_change_me';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE asvosonk TO asvosonk_reports;
GRANT USAGE   ON SCHEMA public       TO asvosonk_reports;
GRANT SELECT  ON ALL TABLES    IN SCHEMA public TO asvosonk_reports;
GRANT SELECT  ON ALL SEQUENCES IN SCHEMA public TO asvosonk_reports;

-- Ensure future tables are also covered
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO asvosonk_reports;
