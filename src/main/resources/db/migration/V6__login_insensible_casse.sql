-- =============================================================
-- V6 — Login insensible à la casse
--
-- « Tresorier » et « tresorier » désignent désormais le même compte : la
-- recherche à la connexion compare LOWER(login). L'unicité doit donc porter
-- sur la forme en minuscules, sans quoi deux comptes pourraient ne différer
-- que par la casse et la connexion deviendrait ambiguë.
--
-- ⚠️ Échoue si deux logins existants ne diffèrent que par la casse : les
-- renommer avant d'appliquer la migration.
-- =============================================================

CREATE UNIQUE INDEX ux_app_user_login_lower ON app_user (LOWER(login));
