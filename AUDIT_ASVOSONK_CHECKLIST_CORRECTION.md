# ✅ Checklist de correction priorisée — ASVOSONK

**Complément au rapport `AUDIT_ASVOSONK_2026-07-13.md`**
Date : 2026-07-13 · Objectif : ordonner la remédiation des ~67 constats en tenant compte des **dépendances entre correctifs**, du risque et de l'effort.

---

## Légende

- **Effort** : 🟢 faible (< 0,5 j) · 🟡 moyen (0,5–2 j) · 🔴 élevé (> 2 j / décision métier requise)
- **Dépend de** : correctif(s) à réaliser avant, sinon la correction est incomplète ou instable
- **Statut** : `[ ]` à faire · `[~]` en cours · `[x]` fait

---

## 🚦 Vague 0 — Prérequis (à faire AVANT tout le reste)

Ces éléments conditionnent la fiabilité de tous les correctifs suivants.

- [ ] **P-0.1 — Mettre en place un socle de tests d'intégration** 🔴
  - Testcontainers PostgreSQL + `@SpringBootTest` qui démarre réellement le contexte (valide le schéma Hibernate) et rejoue les scénarios monétaires.
  - *Pourquoi d'abord :* aucun correctif financier ne doit être livré sans filet de non-régression. Détecte aussi immédiatement **F-02** (crash `validate`).
  - *Couvre / débloque :* tout.

- [ ] **P-0.2 — Geler la mise en production financière** 🟢
  - Décision de gouvernance : pas d'usage réel tant que Vagues 1–2 non terminées.

---

## 🟥 Vague 1 — Bloquant / Critique : disponibilité & démarrage

> À traiter en premier : sans cela, l'application ne démarre pas ou plante à l'usage.

- [ ] **F-02 — `columnDefinition="attendance_status"`** 🟢
  - *Dépend de :* — · *Débloque :* démarrage de l'app. Vérifié automatiquement par **P-0.1**.
- [ ] **F-01 — `SessionStatus` cohérent avec `currentStep`** 🟡
  - Passer `closed` à `REPORT_GENERATED` **ou** remplacer `findByStatus(open)` par `List` + `findFirst`.
  - *Dépend de :* — · *Débloque :* dashboard (F-01), et la logique de F-10 (garde `!isClosed()`).
  - ⚠️ Unifier au passage les 2 chemins de création dupliqués (`SessionService.create` / `CreateSessionUseCase`).
- [ ] **F-27 — Nettoyer les enums orphelins `'default'`** 🟡
  - Migration de reconstruction de type (`CREATE TYPE …_new` + `USING CASE` + `DROP`/`RENAME`) pour `attendance_status` **et** `payment_status`, avec `UPDATE` des lignes résiduelles.
  - *Dépend de :* F-02 (aligner mapping avant de toucher au type) · *Lié à :* F-64, F-65.

---

## 🟥 Vague 2 — Critique : intégrité de la trésorerie

> Cœur du problème financier. **Traiter le socle caisse d'abord** (F-16, F-04), car les autres correctifs monétaires s'appuient dessus.

### 2A. Socle caisse (fondation des flux monétaires)

- [ ] **F-16 — Concurrence sur les soldes** 🔴
  - `@Version` sur `CashboxEntity` (+ retries) **ou** `UPDATE cashbox SET balance = balance + :delta`. Étendre à `RevolvingFundEntity`.
  - *Dépend de :* — · *Débloque :* F-04, F-03, F-05, F-06, F-10 (toute écriture de solde doit être sûre avant d'y ajouter des règles).
- [ ] **F-04 — Contrôle de solde ≥ 0 sur `out`** 🟡
  - Garde dans `CashboxService.record` (`BusinessRuleException` si insuffisant) + `CHECK (balance >= 0)`.
  - *Dépend de :* F-16 · *Lié à :* F-43 (CHECK génériques).
- [ ] **F-38 — Montant ≤ 0 → exception explicite** (au lieu de `null` silencieux) 🟢
  - *Dépend de :* — · *Recommandé avant :* F-05, F-06 (feedback fiable).
- [ ] **F-35 — « Nouveau solde » = solde réel après update** 🟢
  - Relire `cashboxEntity.getBalance()` après merge. *Dépend de :* F-16.

### 2B. Atomicité & double comptabilisation

- [ ] **F-05 — Paiement sanction atomique** 🟡
  - Un seul use-case `@Transactional` appelant `CashboxService.credit` en `MANDATORY`.
  - *Dépend de :* F-04, F-38.
- [ ] **F-10 — Étapes de séance idempotentes** 🟡
  - Vérifier `!isClosed()` + `current == étape attendue` + verrou pessimiste sur la séance + jeton PRG.
  - *Dépend de :* F-01 (statut fiable), F-16.
- [ ] **F-49 — Idempotence cotisation/remboursement (anti double-clic)** 🟡
  - Détection préalable + traduction des violations d'unicité en `BusinessRuleException`. *Dépend de :* F-16.

### 2C. Fonds de roulement & création monétaire

- [ ] **F-03 — Fonds : supprimer l'init à 5 000 + corriger `setBalance(ZERO)`** 🔴 *(décision métier)*
  - Créer à 0 + mouvement de dotation tracé ; remplacer l'écrasement par une soustraction bornée ; recouvrer les avances.
  - *Dépend de :* F-16 · *Nécessite :* validation du bureau sur la règle « compromis 1 000 FCFA ».
- [ ] **F-06 — Plafonner la déduction de sanction créditée** 🟡
  - `min(totalTontine, deductions)` ; ne marquer `paid` que les sanctions couvertes.
  - *Dépend de :* F-04, F-05.

---

## 🟥 Vague 3 — Critique : sécurité & continuité

> Indépendant de la trésorerie — peut être mené **en parallèle** des Vagues 1–2.

- [ ] **F-12 — Secrets hors du dépôt** 🔴
  - Externaliser (variables d'env) ; `.gitignore` ; **purge de l'historique Git** (BFG/filter-repo) ; **rotation** de tous les mots de passe ; forcer le changement de l'admin.
  - *Dépend de :* — · *Lié à :* F-25.
- [ ] **F-25 — Restreindre le rôle `asvosonk_reports`** 🟡
  - GRANT en liste blanche (exclure `app_user`, `role`, `permission`). *Dépend de :* F-12 (rotation du mot de passe reports).
- [ ] **F-15 — Backups fonctionnels** 🟡
  - Injecter `PGPASSWORD` (secret), vérifier fichier non vide, **alerter** en cas d'échec, métrique « dernière sauvegarde OK ». *Dépend de :* F-12.
- [ ] **F-13 — XSS ReportController** 🟡
  - Valider `type ∈ {session,monthly,quarterly}` en amont ; ne jamais renvoyer `getMessage()` brut ; rendu Thymeleaf. *Lié à :* F-14, F-40.
- [ ] **F-14 — Autorisation rapports par type** 🟢
  - `switch(type)` → permission correspondante. *Dépend de :* — · *Souvent corrigé avec :* F-13.
- [ ] **F-11 — Suppression membre non destructive** 🟡 *(décision métier)*
  - FK financières en `RESTRICT` ; « suppression » = changement de statut (`resigned`/`deceased`). *Lié à :* F-26.

---

## 🟧 Vague 4 — Élevé : cohérence métier

- [ ] **F-08 — Invariants de clôture côté backend** 🟡
  - Porter dans les use-cases : `ClosePresenceTourUseCase` (tous ont bénéficié), `MarkPresenceBenefitedUseCase` (ordre + non-doublon), `RecordTontineContributionUseCase` (bénéficiaire non servi + ordre). *Dépend de :* — · *Lié à :* F-09, F-22, F-23, F-24, F-32.
- [ ] **F-09 — Séparer lecture / clôture du tour de présence** 🟡
  - `peekNextBeneficiary` (`readOnly=true`) + clôture dans un flux d'écriture explicite. *Dépend de :* F-08.
- [ ] **F-07 — Corriger le sens des dettes tontine** 🔴 *(re-spécification métier)*
  - Recherche de remboursement : `debtor=contributor, creditor=beneficiary`. **Re-spécifier formellement l'invariant** avant de coder. *Débloque :* F-22, F-23, F-29, F-30. *Nécessite :* P-0.1 (test de rotation complète).
- [ ] **F-22 — Créer la dette sur défaut de cotisation** 🟡 *(dépend du modèle F-07)* · *Dépend de :* F-07.
- [ ] **F-23 — Bloquer la clôture de tour si dettes `owed`** 🟢 · *Dépend de :* F-07.
- [ ] **F-24 — Marquer « bénéficié » seulement après toutes les cotisations** 🟡 · *Dépend de :* F-08.
- [ ] **F-18 — Plafond de prêt vs épargne** 🟡 *(décision métier : ratio)* · *Dépend de :* —.
- [ ] **F-19 — Rejeter/plafonner le sur-remboursement de prêt** 🟢 · *Dépend de :* —.
- [ ] **F-20 — Frais d'adhésion : gérer l'excédent + mouvement de caisse** 🟡 *(décision métier)* · *Dépend de :* F-04.
- [ ] **F-21 — Renommer/clarifier « Total épargnes en caisse »** 🟢 · *Dépend de :* —.
- [ ] **F-17 — Corriger le `OR` de `searchByKeyword`** 🟢 · *Dépend de :* —.
- [ ] **F-47 — Éligibilité prêt : inclure `isActive()`** 🟢 · *Lié à :* F-18.
- [ ] **F-26 — Uniformiser la stratégie de cascade** 🟡 · *Regrouper avec :* F-11.

---

## 🟨 Vague 5 — Moyen : robustesse, reporting, schéma

*(Peut être planifié après stabilisation ; peu de dépendances entre eux.)*

- [ ] **F-40 — ReportService : drainer les flux / `waitFor` d'abord** 🟡
- [ ] **F-42 — `global_movement_view` : montant signé + colonne `direction` dédiée** 🟡 · *Lié à :* F-41
- [ ] **F-41 — Montants en `BigDecimal` (supprimer `double`)** 🟢
- [ ] **F-43 — `CHECK` génériques sur colonnes monétaires** 🟡 · *Dépend de :* F-04 (cohérence des invariants)
- [ ] **F-44 — Fuseau explicite (`Africa/Douala`) / `Clock` injectée** 🟡
- [ ] **F-31 — Unifier le calcul de `totalToTreasurer`** 🟡 · *Dépend de :* F-07, F-30
- [ ] **F-30 — Trancher le modèle tontine ↔ caisse** 🔴 *(décision métier)* · *Dépend de :* F-07
- [ ] **F-29 — Exclure les remboursements du « total collecté »** 🟢 · *Dépend de :* F-07
- [ ] **F-28 — Index unique partiel `tontine_tour(status) WHERE 'open'`** 🟢
- [ ] **F-33 — Formulaire création tour : DTO ligne (memberId+order)** 🟡
- [ ] **F-34 — `drawOrder` : trancher mutable/immuable + sérialiser le calcul du max** 🟡
- [ ] **F-32 — Rejeter `contributorId == beneficiaryId`** 🟢
- [ ] **F-36 — Écran de réconciliation solde ↔ Σ mouvements** 🟡
- [ ] **F-37 — `CloseCashboxUseCase` : statut open/closed OU suppression** 🟡
- [ ] **F-39 — Annulation sanction : motif obligatoire + réversion fonds** 🟡
- [ ] **F-45 — Migrations futures avec reprise de données (pas de DROP sec)** 🟢 *(process)*
- [ ] **F-46 — Réduire timeout session / retirer exclusion CSRF `/api/**` / corriger doc port** 🟢
- [ ] **F-48 — Pagination + filtres SQL sur l'historique caisse** 🟡

---

## ⬜ Vague 6 — Faible : dette technique & nettoyage

*(Regroupables en un « sprint qualité ».)*

- [ ] **F-61** Retirer le `@UniqueConstraint` obsolète de `TontineDebtEntity` 🟢 *(à faire tôt : piège si `ddl-auto=update`)*
- [ ] **F-60** Faire hériter les entités de `BaseEntity` (`@MappedSuperclass`) 🟡
- [ ] **F-53 / F-51** Centraliser les constantes de prêt + arrondir l'intérêt en FCFA entier 🟢
- [ ] **F-50** Supprimer le planificateur d'emprunts en double 🟢
- [ ] **F-52** Passer `member`/`refId` aux mouvements de caisse banque 🟢
- [ ] **F-54 / F-55** Corriger badges de statut + barre de progression de prêt 🟢
- [ ] **F-56 / F-58** Validations formulaires (`@PastOrPresent`, `drawOrder > 0`) 🟢
- [ ] **F-57** `allBenefited` : ajouter `&& !participants.isEmpty()` 🟢
- [ ] **F-59** Uniformiser `NUMERIC(12,2)` sur tous les montants 🟢
- [ ] **F-62 / F-64** Supprimer champs/valeurs morts (`tontineSanctionDeductions`, `recovered`) 🟢
- [ ] **F-63** Uniformiser la gestion d'erreur (`ResourceNotFoundException`) 🟢
- [ ] **F-65 / F-66 / F-67** Consolider migrations enum · corriger commentaire backup · robustifier module rapports 🟡

---

## 📊 Graphe de dépendances (chemin critique)

```
P-0.1 (tests) ──┬─────────────────────────────────────────────► valide tout
                │
F-02 ──► (démarrage) ──► F-27
F-01 ──► F-10
                                   ┌─► F-04 ──► F-05 ──► F-06
F-16 (concurrence) ────────────────┼─► F-03
                                   ├─► F-35, F-38, F-49
                                   └─► F-43
F-12 (secrets) ──► F-25 ──► F-15
F-13 ──► F-14
F-07 (dettes) ──► F-22, F-23, F-29, F-30 ──► F-31
F-08 ──► F-09, F-24
F-11 ──► F-26
```

**Chemins critiques (à démarrer en priorité) :**
1. `P-0.1 → F-02 → F-01` *(rendre l'app démarrable et utilisable)*
2. `F-16 → F-04 → F-05/F-06` *(sécuriser l'argent)*
3. `F-12 → F-25/F-15` *(sécurité & continuité, en parallèle)*
4. `F-07` *(débloque tout le pan tontine — nécessite re-spécification métier au préalable)*

---

## 🗓️ Séquencement recommandé

| Sprint | Contenu | Sortie attendue |
|---|---|---|
| **S1** | P-0.1, P-0.2, F-02, F-01, F-27 | App démarrable + dashboard stable + CI qui valide le schéma |
| **S2** | F-16, F-04, F-38, F-35, F-05, F-10, F-49 | Trésorerie sûre (concurrence, soldes, atomicité) |
| **S3** | F-12, F-25, F-15, F-13, F-14 *(parallélisable dès S1)* | Sécurité & sauvegardes |
| **S4** | F-03, F-06, F-07 *(+ re-spéc métier)*, F-22, F-23 | Fonds & tontine corrigés |
| **S5** | F-08, F-09, F-11, F-18/19/20/21, F-17, F-47, F-26 | Cohérence métier & garde-fous |
| **S6** | Vague 5 (robustesse/reporting/schéma) | Fiabilisation |
| **S7** | Vague 6 (dette technique) | Nettoyage |

> Décisions métier à obtenir du bureau **avant** S4 : règle du « compromis 1 000 FCFA » du fonds (F-03), ratio de plafond de prêt (F-18), modèle de dette tontine et son articulation avec la caisse (F-07/F-30), politique de suppression de membre (F-11).

---

*Checklist dérivée du rapport `AUDIT_ASVOSONK_2026-07-13.md`. Les identifiants F-xx y renvoient pour le détail complet de chaque constat.*
