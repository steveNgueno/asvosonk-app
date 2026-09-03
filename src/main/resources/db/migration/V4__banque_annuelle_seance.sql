-- =============================================================
-- ASVOSONK — Banque Annuelle saisie en séance
--
-- Épargnes, emprunts et remboursements peuvent désormais être
-- saisis pendant une séance, à l'étape « Banque Annuelle ». Le
-- rattachement est porté par la ligne métier elle-même : le
-- rapport de séance se calcule sur ces tables plutôt qu'en
-- devinant la nature d'un mouvement de caisse à partir de son
-- libellé — un libellé changé faussait silencieusement les
-- chiffres figés.
--
-- La colonne reste nullable : les opérations saisies hors séance,
-- depuis la page Banque Annuelle, ne sont rattachées à aucune
-- séance. ON DELETE SET NULL : supprimer une séance ne doit
-- jamais effacer une épargne ou un remboursement.
-- =============================================================

ALTER TABLE saving
    ADD COLUMN session_id BIGINT REFERENCES meeting_session(id) ON DELETE SET NULL;

ALTER TABLE loan
    ADD COLUMN session_id BIGINT REFERENCES meeting_session(id) ON DELETE SET NULL;

ALTER TABLE loan_repayment
    ADD COLUMN session_id BIGINT REFERENCES meeting_session(id) ON DELETE SET NULL;

CREATE INDEX idx_saving_session         ON saving(session_id);
CREATE INDEX idx_loan_session           ON loan(session_id);
CREATE INDEX idx_loan_repayment_session ON loan_repayment(session_id);

-- Troisième chiffre de la rubrique, à côté des épargnes collectées et des
-- remboursements encaissés : le montant décaissé en emprunts. Il n'apparaissait
-- jusqu'ici que noyé dans total_outflow.
ALTER TABLE session_report
    ADD COLUMN banque_annuelle_loans NUMERIC(12,2) NOT NULL DEFAULT 0;
