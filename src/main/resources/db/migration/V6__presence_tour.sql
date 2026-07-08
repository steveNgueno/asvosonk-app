-- =============================================================
-- ASVOSONK — Presence Tour schema v6
--
-- Creates the presence tour system that tracks beneficiary rotation
-- for meeting session attendance.
-- =============================================================

CREATE TYPE presence_tour_status AS ENUM ('open', 'closed');

CREATE TABLE presence_tour (
    id          BIGSERIAL PRIMARY KEY,
    start_date  DATE                NOT NULL,
    end_date    DATE,
    status      presence_tour_status NOT NULL DEFAULT 'open',
    created_at  TIMESTAMP           NOT NULL DEFAULT now()
);

CREATE TABLE presence_tour_participant (
    id            BIGSERIAL PRIMARY KEY,
    tour_id       BIGINT  NOT NULL REFERENCES presence_tour(id)  ON DELETE CASCADE,
    member_id     BIGINT  NOT NULL REFERENCES member(id)         ON DELETE CASCADE,
    draw_order    INT     NOT NULL,
    has_benefited BOOLEAN NOT NULL DEFAULT false,
    session_id    BIGINT  REFERENCES meeting_session(id) ON DELETE SET NULL,
    created_at  TIMESTAMP           NOT NULL DEFAULT now(),
    UNIQUE (tour_id, member_id),
    UNIQUE (tour_id, draw_order)
);

CREATE INDEX idx_pt_participant_tour   ON presence_tour_participant(tour_id);
CREATE INDEX idx_pt_participant_member ON presence_tour_participant(member_id);
CREATE INDEX idx_pt_status             ON presence_tour(status);
