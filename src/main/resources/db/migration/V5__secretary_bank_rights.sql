-- =============================================================
-- ASVOSONK — Droits Banque Annuelle du secrétaire
--
-- Le seed initial excluait explicitement le secrétaire de la
-- Banque Annuelle : la rubrique ne vivait alors qu'en dehors de
-- la séance, dans le domaine du trésorier et du président.
--
-- Elle se saisit désormais pendant la séance, à l'étape
-- BANQUE_ANNUELLE_OPEN — et c'est le secrétaire qui tient la
-- feuille. Sans ces droits il voyait la rubrique sans aucun
-- bouton, et /bank lui répondait 403 : l'étape était
-- inutilisable par la seule personne présente pour la remplir.
--
-- Le contrôle reste assuré en aval : le rapport de séance fige
-- les trois totaux de la rubrique, et le censeur comme
-- l'auditeur gardent leur accès en lecture.
-- =============================================================

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'SECRETARY'
   AND p.code IN ('BANK_VIEW', 'BANK_SAVING_RECORD',
                  'BANK_LOAN_CREATE', 'BANK_LOAN_REPAYMENT')
ON CONFLICT (role_id, permission_id) DO NOTHING;
