# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## fm-admin

Admin backend service for the FinMates platform.
Port: 8090 (dev), 80 (k8s)
Java: 21, Spring Boot 3.3.5

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
```

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

Both `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` are excluded.
All JPA infrastructure is configured manually in `DataSourceConfig.java`:

- `mainDataSource` / `mainEntityManagerFactory` / `mainTransactionManager` — marked `@Primary`
- `cryptoDataSource` / `cryptoEntityManagerFactory` / `cryptoTransactionManager` — non-primary
- Entity scan packages: `entity.main` → main DB, `entity.crypto` → crypto DB
- Repository scan packages: `repository.main` → main, `repository.crypto` → crypto
  (configured via `MainRepositoryConfig` and `CryptoRepositoryConfig`)

Service methods that write to the crypto DB must specify `@Transactional("cryptoTransactionManager")`.
Read-only service methods can omit the manager (default resolves to @Primary = main).

## Security

All `/api/admin/**` endpoints require `ROLE_ADMIN` JWT claim.
Roles are extracted from Keycloak's nested `realm_access.roles` claim and uppercased:
`"admin"` → `ROLE_ADMIN`. Regular users receive 403.

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

## Known Gotchas

- **HikariCP requires `jdbc-url`, not `url`** — when using custom `@ConfigurationProperties` prefix (e.g. `datasource.main.*`), HikariCP does not get Spring Boot's auto-mapping of `url` → `jdbcUrl`. Use `datasource.main.jdbc-url` in properties files. Using `url` with `driver-class-name` causes `IllegalArgumentException: jdbcUrl is required with driverClassName` at startup.

## Package Layout

```
config/
  DataSourceConfig.java         — dual datasource + EntityManagerFactory + TransactionManager
  SecurityConfig.java           — JWT auth, CORS, role extraction from realm_access.roles
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
