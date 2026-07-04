# ASVOSONK — Application de gestion financière

Association des Voisins Solidaires de Nkozoa « Nkou-Assi »

---

## Prérequis à installer sur le laptop

| Outil | Version | Lien |
|---|---|---|
| Java (JDK) | 21 LTS | https://adoptium.net |
| Maven | 3.9.x | https://maven.apache.org |
| Docker Desktop | Dernière version | https://www.docker.com/products/docker-desktop |
| IntelliJ IDEA Community | Dernière version | https://www.jetbrains.com/idea/download |

> **Docker Desktop remplace l'installation manuelle de PostgreSQL.**
> Une fois Docker installé, une seule commande suffit pour démarrer la base de données.

---

## Démarrage en 4 étapes

### 1. Démarrer la base de données (PostgreSQL + pgAdmin)

Ouvrir un terminal dans le dossier du projet et lancer :

```bash
docker-compose up -d
```

Attendre que les conteneurs soient démarrés (~20 secondes).
Vérifier que tout tourne :

```bash
docker-compose ps
```

Les deux services doivent afficher **"healthy"** ou **"running"**.

### 2. Ouvrir le projet dans IntelliJ

1. `File → Open` → sélectionner le dossier `asvosonk-app`
2. IntelliJ détecte automatiquement le projet Maven
3. Attendre le téléchargement des dépendances (panneau Maven en bas à droite)
4. Vérifier qu'il n'y a aucune erreur rouge

### 3. Lancer l'application

Ouvrir `src/main/java/org/asvosonk/AsvosonkApplication.java`
et cliquer sur le bouton ▶ vert.

Au premier démarrage, **Flyway** exécute automatiquement les scripts SQL :
- `V1__asvosonk_schema.sql` → crée toutes les tables
- `V2__asvosonk_seed.sql` → insère les rôles, permissions et le compte admin

Message attendu dans la console :
```
Successfully applied 2 migrations to schema "public"
Started AsvosonkApplication in X.XXX seconds
```

### 4. Accéder à l'application

Ouvrir le navigateur (Chrome ou Edge) et aller sur :

```
http://localhost:8080
```

**Connexion par défaut :**
- Login : `admin`
- Mot de passe : `Admin@2024`

> ⚠️ Changer ce mot de passe dès la première connexion via le module Utilisateurs.

---

## Interface d'administration de la base de données (pgAdmin)

Pour consulter et administrer la base de données directement :

```
http://localhost:5050
```

- Email : `admin@asvosonk.local`
- Mot de passe : `PgAdmin@2024`

La connexion au serveur PostgreSQL est pré-configurée automatiquement.

---

## Commandes Docker utiles

```bash
# Démarrer les conteneurs
docker-compose up -d

# Arrêter les conteneurs (données conservées)
docker-compose down

# Voir les logs en temps réel
docker-compose logs -f

# Redémarrer un service spécifique
docker-compose restart postgres

# ⚠️ Supprimer les conteneurs ET toutes les données
docker-compose down -v
```

---

## Structure du projet

Voir `PROJECT_STRUCTURE.md` pour l'arborescence complète avec le statut de chaque lot.

---

## Comptes et mots de passe (à changer en production)

| Service | Identifiant | Mot de passe |
|---|---|---|
| Application (admin) | `admin` | `Admin@2024` |
| PostgreSQL (app) | `asvosonk_app` | `AsvosonkDb@2024` |
| pgAdmin | `admin@asvosonk.local` | `PgAdmin@2024` |

Tous ces mots de passe sont configurables dans le fichier `.env`
(pour Docker) et `application.properties` (pour Spring Boot).
