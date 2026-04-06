# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## fm-admin

Admin backend service for the FinMates platform.
Port: 8090 (dev), 80 (k8s)
Java: 21, Spring Boot 3.3.5

## Service Purpose

Admin-only backend for aggregating user, portfolio, trade, and news data from the `main` and `crypto` databases. All endpoints require `ROLE_ADMIN` JWT claim. Read-mostly: minimal writes (news CRUD only); portfolio/position deletes are admin overrides, not regular user operations.

## Build & Run

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Run with dev profile (port 8090)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Compile only (fast check)
mvn clean compile

# Run a single test
mvn test -Dtest=ClassName

# Run all tests
mvn test
```

**Windows users:** Use `.\mvnw.cmd` instead of `mvn`.

Swagger UI: http://localhost:8090/ui
API docs:   http://localhost:8090/v3/api-docs

## Databases

Connects to TWO databases simultaneously — owns neither schema:

| Datasource | DB | Tables used |
|------------|-----|-------------|
| `mainDataSource` (@Primary) | `main` (finmates-main DB) | `users`, `admin_news` |
| `cryptoDataSource` | `crypto` | `portfolios`, `portfolio_trades`, `portfolio_positions` |

**Flyway is DISABLED** — this service never creates or modifies schemas.
The `admin_news` table was created manually (V11__admin_news.sql executed directly against
the dev DB at finmates.com:5432/main). finmates-main uses ddl-auto=update without Flyway,
so migration files there are reference-only. For a new environment run:
  psql -h <host> -U user -d main -f finmates-main/src/main/resources/db/migration/V11__admin_news.sql

## Dual Datasource Architecture

Both `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` are excluded in `application.properties`.
All JPA infrastructure is configured manually in `DataSourceConfig.java`:

- `mainDataSource` / `mainEntityManagerFactory` / `mainTransactionManager` — marked `@Primary`
- `cryptoDataSource` / `cryptoEntityManagerFactory` / `cryptoTransactionManager` — non-primary
- Entity scan packages: `entity.main` → main DB, `entity.crypto` → crypto DB
- Repository scan packages: `repository.main` → main, `repository.crypto` → crypto
  (configured via `MainRepositoryConfig` and `CryptoRepositoryConfig`)
- Naming strategy: `CamelCaseToUnderscoresNamingStrategy` — `firstName` field → `first_name` column
- DDL: `ddl-auto=none` — service never modifies schema (read-only on crypto DB; news table in main is pre-created)

**Transaction management:**
- Read-only methods (no `@Transactional`) → default to `mainTransactionManager` (@Primary)
- Crypto DB writes (portfolio/position deletes) → specify `@Transactional("cryptoTransactionManager")`
- News CRUD (main DB writes) → no explicit transaction manager needed (uses primary)

## Security

All `/api/admin/**` endpoints require `ROLE_ADMIN` JWT claim (extracted by `jwtAuthConverter()` in `SecurityConfig`). Regular users receive 403.

Public paths: `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`, `/ui/**`

## API Endpoints

```
GET    /api/admin/users                   paginated user list (?search=)
GET    /api/admin/users/{id}              user detail + portfolio summaries from crypto DB
PUT    /api/admin/users/{id}/enabled      toggle isActive { "enabled": true/false }
DELETE /api/admin/users/{id}              soft delete (sets deleted_at, isActive=false)

GET    /api/admin/portfolios              all portfolios (?userId=)
GET    /api/admin/portfolios/{id}         portfolio detail with positions + tradeCount
DELETE /api/admin/portfolios/{id}/positions/{symbol}  delete a position row
POST   /api/admin/portfolios/{id}/reset   reset cashBalance=initialBalance, delete all positions

GET    /api/admin/trades                  all trades (?portfolioId= &symbol= &userId=)
GET    /api/admin/positions               all positions (?symbol= &userId=)

GET    /api/admin/news                    news articles (newest first)
POST   /api/admin/news                    create article
PUT    /api/admin/news/{id}               update article
DELETE /api/admin/news/{id}              delete article

GET    /api/admin/stats                   platform-wide counts (both DBs)
```

## Entity Notes

- `AdminUser.isActive` → `is_active` column in `users` table; `isActive=false` + `deletedAt=now()` = soft delete
- `AdminPortfolio.type` / `.provider` — stored as VARCHAR in DB (EnumType.STRING in finmates-crypto)
- `AdminTrade.side` (BUY|SELL) and `.status` (OPEN|CLOSED|CANCELLED) — VARCHAR in DB
- `AdminNewsArticle.symbols` — stored as comma-separated string ("BTC,ETH"); parsed to `List<String>` in `AdminNewsService`

## Role Extraction (Keycloak JWT)

`SecurityConfig.jwtAuthConverter()` extracts roles from **both** Keycloak JWT scopes:
- **Realm-level roles** (`realm_access.roles`) — e.g., `["admin"]` → `ROLE_ADMIN`
- **Client-level roles** (`resource_access.<clientId>.roles`) — searched across all clients

Both are uppercased and prefixed with `ROLE_` so `@PreAuthorize("hasRole('ADMIN')")` works correctly. A user needs either realm-level `admin` role OR client-level `admin` role in any client.

## Known Gotchas

- **HikariCP requires `jdbc-url`, not `url`** — when using custom `@ConfigurationProperties` prefix (e.g. `datasource.main.*`), HikariCP does not get Spring Boot's auto-mapping of `url` → `jdbcUrl`. Use `datasource.main.jdbc-url` in `application.properties` and `application-dev.properties`. **BUG in `application-k8s.properties`**: uses `url` instead of `jdbc-url`. When deploying to Kubernetes, either rename to `jdbc-url` or the service will fail to initialize with `IllegalArgumentException: jdbcUrl is required with driverClassName`.

- **Self-signed Keycloak cert (dev profile)** — `auth.finmates.com` uses a self-signed TLS cert that the local JVM cannot validate. Spring Boot's default `JwtDecoder` (issuer-uri discovery) fails with `JwtDecoderInitializationException` / PKIX path building failed on first authenticated request. **Fixed** in `SecurityConfig.java`: a custom `JwtDecoder` bean uses `NimbusJwtDecoder.withJwkSetUri()` with a trust-all `SimpleClientHttpRequestFactory`, bypassing PKIX only for JWK Set fetches. The JWK URI is `{issuer-uri}/protocol/openid-connect/certs`. Issuer claim validation is preserved via `JwtValidators.createDefaultWithIssuer()`.

## Package Layout

```
config/
  DataSourceConfig.java         — dual datasource + EntityManagerFactory + TransactionManager
  SecurityConfig.java           — JWT auth, CORS, role extraction from realm_access + resource_access
  MainRepositoryConfig.java     — @EnableJpaRepositories for repository.main
  CryptoRepositoryConfig.java   — @EnableJpaRepositories for repository.crypto

entity/
  main/    AdminUser, AdminNewsArticle
  crypto/  AdminPortfolio, AdminTrade, AdminPosition

repository/
  main/    AdminUserRepository, AdminNewsRepository
  crypto/  AdminPortfolioRepository, AdminTradeRepository, AdminPositionRepository

dto/       All DTOs + CreateNewsRequest

service/   AdminUserService, AdminPortfolioService, AdminTradeService,
           AdminPositionService, AdminNewsService, AdminStatsService

controller/ AdminUserController, AdminPortfolioController, AdminTradeController,
            AdminPositionController, AdminNewsController, AdminStatsController
```
