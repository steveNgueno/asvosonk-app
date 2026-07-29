# Remédiation sécurité — actions manuelles requises (F-12)

Ce document liste les actions **hors code** que le correctif F-12 ne peut pas
réaliser automatiquement. Elles doivent être exécutées par l'équipe
d'exploitation avant toute mise en production.

## 1. Externalisation des secrets (fait côté code)

`application.properties` lit désormais les identifiants DB via variables
d'environnement (`ASVOSONK_DB_URL`, `ASVOSONK_DB_USERNAME`,
`ASVOSONK_DB_PASSWORD`). Les valeurs par défaut ne servent qu'au développement
local (docker-compose). **En production, définir ces variables dans
l'environnement** (systemd, Docker secrets, coffre-fort…), jamais dans un
fichier versionné.

Le module Python (`python-reports/db_config.py`) lit déjà `ASVOSONK_DB_*`.

## 2. Rotation des mots de passe (à faire en production)

Tous les mots de passe présents dans l'historique Git sont **compromis** et
doivent être changés :

- `AsvosonkDb@2024` (rôle `asvosonk_app`) → nouveau secret fort.
- `reports_pwd_change_me` (rôle `asvosonk_reports`) → nouveau secret fort
  (puis mettre à jour `ASVOSONK_DB_PASSWORD` du module rapports).
- pgAdmin (`PgAdmin@2024`) → nouveau secret fort.
- Compte admin applicatif `admin` / `Admin@2024` → **forcer le changement au
  premier login** (voir §4) ou réinitialiser le hash.

## 3. Purge de l'historique Git

Les secrets ont été committés ; les retirer du HEAD ne suffit pas. Purger
l'historique avec `git filter-repo` (recommandé) ou BFG :

```bash
git filter-repo --replace-text <(echo 'AsvosonkDb@2024==>REDACTED')
```

Puis forcer la mise à jour du dépôt distant et faire re-cloner par l'équipe.
**Prérequis :** coordination d'équipe (réécriture d'historique).

## 4. Mot de passe admin par défaut

Le seed V2 crée `admin` / `Admin@2024` (hash BCrypt en clair dans la migration).
Options :
- réinitialiser le hash en production après premier déploiement, **ou**
- ajouter un indicateur « mot de passe à changer » forçant la mise à jour au
  premier login (évolution applicative — hors périmètre du correctif F-12).

## 5. Rôle reporting (F-25)

Voir la migration de restriction des GRANT (liste blanche) : le rôle
`asvosonk_reports` ne doit plus pouvoir lire `app_user`, `role`, `permission`.
