# IE-Eff — Shoe Factory Efficiency Management

Hệ thống quản lý hiệu suất sản xuất giày (Industrial Efficiency) cho **Công ty CP Giày Thiên Lộc**. Theo dõi năng suất, tính KPI (PPH, Efficiency), quản lý master data, import/export Excel, sinh báo cáo lương / tuần / 6S, và lưu lịch sử thay đổi.

## Tech Stack

| Layer        | Technology                                                                 |
|--------------|----------------------------------------------------------------------------|
| Backend      | Spring Boot 4.0.3, Java 17                                                 |
| Frontend     | Thymeleaf SSR + một số trang `/api/v1/**` REST (đang refactor)             |
| Database     | **PostgreSQL 15+**                                                         |
| Security     | Spring Security (BCrypt, Role-based: ADMIN / MANAGER / USER), CSRF         |
| ORM          | Spring Data JPA / Hibernate 6                                              |
| Migration    | Flyway (V1..V7, validate-only ở prod)                                      |
| Excel        | Apache POI 5.2.5                                                           |
| Scheduling   | Quartz (JDBC store) + Spring `@Scheduled`                                  |
| Resilience   | Resilience4j circuit breakers (salary / weekly reports)                    |
| Cache        | Caffeine + Spring Cache                                                    |
| Observability| Spring Actuator (health/info/metrics), Logstash JSON logs, Correlation IDs |
| i18n         | `vi` (default) + `en`                                                      |
| Build        | Maven                                                                      |

## Repository Layout

```
src/main/
├── java/thienloc/manage/
│   ├── controller/        ← SSR Thymeleaf controllers
│   ├── controller/api/    ← REST /api/v1/** (in-flight refactor)
│   ├── service/           ← Business logic (eff calc, imports, salary, etc.)
│   ├── repository/        ← Spring Data JPA
│   ├── entity/            ← JPA entities
│   ├── dto/               ← Request / response DTOs
│   ├── security/          ← SecurityConfig, CustomUserDetailsService
│   ├── config/            ← Quartz, Cache, Async, WebMvc, seed initializers
│   ├── scheduler/         ← Retention jobs
│   ├── health/            ← Custom actuator health indicators
│   ├── interceptor/       ← Rate limit
│   ├── filter/            ← Correlation ID
│   └── exception/         ← Global + REST exception handlers
└── resources/
    ├── application.properties           ← dev defaults
    ├── application-prod.properties      ← prod (env-driven)
    ├── db/migration/V*.sql              ← Flyway schema
    ├── messages*.properties             ← i18n
    ├── static/                          ← css/js
    └── templates/                       ← Thymeleaf

docs/
├── SERVER_CONFIG.md       ← bare-metal Windows Server tuning guide
├── db-bootstrap.sql       ← role + database bootstrap
DEPLOY.md                  ← production deploy guide (bare-metal + Docker)
```

## Prerequisites (Dev)

- **Java 17** (Eclipse Temurin recommended) — https://adoptium.net/
- **Maven 3.8+**
- **PostgreSQL 15+** locally, or use the Docker stack (see DEPLOY.md)

## Getting Started

### 1. Clone

```bash
git clone <repo-url>
cd IE-Eff
```

### 2. Create a local PostgreSQL database

```bash
psql -U postgres -h localhost -f docs/db-bootstrap.sql
```

This creates the `ie_app` role and `shoe_eff_db` database. **Change the placeholder password in `docs/db-bootstrap.sql` first.**

### 3. Configure connection

By default `application.properties` uses `localhost:5432 / postgres / <DB_PASSWORD env>`. Override via environment variables if your local Postgres differs:

```powershell
$env:DB_USERNAME = "ie_app"
$env:DB_PASSWORD = "your-dev-password"
```

### 4. Run

```bash
mvn spring-boot:run
```

App starts at **http://localhost:8080**. Flyway runs migrations on first boot.

## Default Dev Credentials

| Username  | Password    | Role    |
|-----------|-------------|---------|
| `admin`   | `Admin@123` | ADMIN   |
| `manager` | `Manager@123` | MANAGER |

These ship **only via the `dev` profile defaults** in `application.properties`. In production, the seed user passwords must be set via `APP_DEFAULT_ADMIN_PASSWORD` / `APP_DEFAULT_MANAGER_PASSWORD` env vars or the app refuses to start. Change them on first login.

## Environment Variables

| Variable                       | Required        | Default (dev) | Description                                          |
|--------------------------------|-----------------|---------------|------------------------------------------------------|
| `DB_URL`                       | Prod only       | `jdbc:postgresql://localhost:5432/shoe_eff_db` | JDBC URL                  |
| `DB_USERNAME`                  | Prod only       | `postgres`    | Database user                                        |
| `DB_PASSWORD`                  | **Yes**         | (empty)       | Database password — app fails fast if unset          |
| `APP_DEFAULT_ADMIN_PASSWORD`   | Prod: **Yes**   | `Admin@123`   | Seed password for the `admin` user                   |
| `APP_DEFAULT_MANAGER_PASSWORD` | Prod: **Yes**   | `Manager@123` | Seed password for the `manager` user                 |
| `PORT`                         | No              | `8080`        | HTTP port                                            |
| `SPRING_PROFILES_ACTIVE`       | Prod: `prod`    | (none)        | Activate `application-prod.properties`               |

## Build a Runnable JAR

```bash
mvn clean package -DskipTests
# Output: target/management-0.0.1-SNAPSHOT.jar
```

## Tests

```bash
mvn test
```

> Some SSR controller tests are currently broken pending the REST refactor (slice 2). See git history.

## Deployment

See **[DEPLOY.md](DEPLOY.md)** for:

- Docker / Docker Compose (recommended for WSL Ubuntu)
- Windows Server bare-metal with NSSM
- Reverse-proxy (TLS) configuration
- Backup strategy

Bare-metal server sizing and PostgreSQL tuning: **[docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md)**.

## License

Internal — Công ty CP Giày Thiên Lộc.
