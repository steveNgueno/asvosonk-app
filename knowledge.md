# Project knowledge

This file gives Codebuff context about your project: goals, commands, conventions, and gotchas.

## What this project is
**ASVOSONK** — Financial management application for a Cameroonian community association (Association des Voisins Solidaires de Nkozoa « Nkou-Assi »). Manages member registrations, weekly sessions (presence), tontine contributions, revolving fund, loans/savings, cashboxes, sanctions, and reports.

## Quickstart

### Prerequisites
- Java 21 (JDK) — https://adoptium.net
- Maven 3.9.x
- Docker Desktop

### Setup & Run
```bash
# 1. Start PostgreSQL + pgAdmin
docker-compose up -d

# 2. Run the app (Flyway auto-applies migrations)
mvn spring-boot:run

# Or via the main class: src/main/java/org/asvosonk/AsvosonkApplication.java
```

App at: http://localhost:8085
pgAdmin at: http://localhost:5050
Default login: `admin` / `Admin@2024`

### Docker commands
```bash
docker-compose up -d          # Start services
docker-compose down           # Stop (data preserved)
docker-compose down -v        # ⚠️ Stop + delete all data
docker-compose logs -f        # Live logs
```

## Commands
- **Build**: `mvn clean compile`
- **Test**: `mvn test`
- **Run**: `mvn spring-boot:run`
- **Package**: `mvn clean package`

## Architecture
### Key directories
| Directory | Purpose |
|---|---|
| `src/main/java/org/asvosonk/` | Java source (controllers, services, entities, repos) |
| `src/main/resources/templates/` | Thymeleaf HTML templates |
| `src/main/resources/static/css/` | Static CSS |
| `src/main/resources/db/migration/` | Flyway SQL migrations |
| `src/main/resources/application.properties` | App config |

### Packages (org.asvosonk)
- `member/` — Member CRUD, membership fees (controller, service, entity, repository)
- `session/` — Meeting sessions, attendance, revolving fund (controller, service, entity, repository)
- `cashbox/` — Cashbox management (entity, repository, service)
- `sanction/` — Sanctions (entity, repository)
- `security/` — App users, roles, permissions, Spring Security config
- `web/` — Dashboard controller

### Data flow
Controller (Spring MVC) → Service (business logic) → Repository (JPA/Hibernate) → PostgreSQL via Flyway-managed schema. Thymeleaf templates render the UI with Spring Security tags for permissions.

### Stack
- **Backend**: Spring Boot 3.3.0, Java 21, Spring Data JPA, Spring Security 6, Thymeleaf
- **Database**: PostgreSQL 16 (Docker), Flyway for migrations
- **Build**: Maven, Lombok, Hibernate Validator

## Conventions
- **DB schema**: English names, `snake_case`, all tables in `public` schema
- **SQL migrations**: Flyway files named `V{number}__{description}.sql`
- **Entities**: JPA annotations, `@Entity`, `@Table`, field names in `camelCase`
- **Lombok**: Used for getters/setters/constructors (`@Data`, `@NoArgsConstructor`, etc.)
- **Thymeleaf**: Layout via `layout.html`, Spring Security tags for permission checks
- **Security**: Role-based access (PRESIDENT, SECRETARY, TREASURER, AUDITOR, CENSOR) with granular permissions
- **Ports**: App = 8085, PostgreSQL = 5433, pgAdmin = 5050 (all localhost-only)
- **Password hashing**: BCrypt (strength 12)
- **Logging**: `logs/asvosonk.log`, rolling policy (10MB per file, 30 days history)

## Things to avoid
- Don't modify the DB schema directly — always add a new Flyway migration
- Don't commit `.env` files or sensitive credentials
- Don't expose PostgreSQL or pgAdmin ports beyond `127.0.0.1` in production
- Don't skip `ddl-auto=validate` — Flyway owns schema changes, Hibernate only validates
