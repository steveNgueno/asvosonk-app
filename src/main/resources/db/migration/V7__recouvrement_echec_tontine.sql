-- =============================================================
-- V7 — Recouvrement des échecs de Grande Tontine
--
-- Un membre qui a fait un échec (cotisation 0) à une séance doit recouvrir
-- cet échec — verser au bénéficiaire de cette séance-là — avant de pouvoir
-- cotiser au bénéficiaire du jour. Chaque recouvrement est rattaché à l'échec
-- qu'il solde (une seule fois) et à la séance où il a été versé.
--
-- Le montant suit les règles d'une cotisation : la somme exacte due si le
-- membre avait une dette envers ce bénéficiaire (il avait déjà bénéficié),
-- sinon un multiple de 5 000 FCFA non nul, au choix du membre.
-- =============================================================

CREATE TABLE tontine_recovery (
    id                     BIGSERIAL PRIMARY KEY,
    tour_id                BIGINT        NOT NULL REFERENCES tontine_tour(id)         ON DELETE CASCADE,
    failed_contribution_id BIGINT        NOT NULL UNIQUE
                                         REFERENCES tontine_contribution(id)          ON DELETE RESTRICT,
    session_id             BIGINT        NOT NULL REFERENCES meeting_session(id)      ON DELETE CASCADE,
    contributor_id         BIGINT        NOT NULL REFERENCES member(id)               ON DELETE RESTRICT,
    beneficiary_id         BIGINT        NOT NULL REFERENCES member(id)               ON DELETE RESTRICT,
    amount                 NUMERIC(10,2) NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_tontine_recovery_amount_pos CHECK (amount > 0)
);

CREATE INDEX idx_tontine_recovery_session     ON tontine_recovery(session_id);
CREATE INDEX idx_tontine_recovery_contributor ON tontine_recovery(contributor_id);
