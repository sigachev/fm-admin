# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Before Starting Any Task
1. Read claude mem.md for session memory
2. For architectural questions, check `graphify-out/GRAPH_REPORT.md`

## fm-admin

Admin backend service for the FinMates platform.
Port: 8090 (dev), 80 (k8s)
Java: 21, Spring Boot 3.3.5

## Service Purpose

Admin-only backend for managing users, portfolios, trades, news, and token/asset metadata across the platform. All endpoints require `ROLE_ADMIN` JWT claim.

**Data ownership model:**
- `main` DB — read-only on `users`, read-write on `admin_news` + `audit_log` (V11/V15 in finmates-main)
- ~~`crypto` DB~~ — no longer used (2026-11-21): finmates-crypto V27 "verified leaderboard drop virtual trading" dropped `portfolios` / `portfolio_trades` / `portfolio_positions`, so fm-admin's portfolio/trade/position admin and the crypto datasource were removed.
- `crypto_data` DB — read-write on `asset`, `news_article`, `news_source`; admin news is **mirrored** here via `AdminNewsService` for public display (best-effort, non-blocking)

**Do NOT modify user, portfolio, or trade schemas directly.** All schema changes in those databases must be made in their respective services (`finmates-main`, `finmates-crypto`, `fm-crypto-aggregator`).

## Development Patterns

### Moderation Orchestration Pattern (Prompt 6b)

Moderation actions follow this sequence:
1. **Controller** calls `AdminModerationService` with the `Jwt` principal
2. **AdminModerationService** calls `FmSocialClient` or `FmMainClient` (RestTemplate, `X-Internal-Secret` header) for the actual state change
3. **AuditLogService.record()** writes the `audit_log` row in the same @Transactional scope (main DB)
4. If the HTTP call to fm-social/fm-main fails, an exception is thrown before the audit write — keeping them consistent

**Never call fm-social or finmates-main DB directly from fm-admin.** Always go through `FmSocialClient` / `FmMainClient`.

**JWT actor extraction pattern:**
```java
// In AdminModerationService.resolveActorDbId(Jwt jwt):
adminUserRepository.findByKeycloakId(jwt.getSubject()).map(AdminUser::getId).orElse(null)
// In AuditLogService.record(AuditAction, ..., Jwt actorJwt):
actorJwt.getClaimAsString("preferred_username") → actorUsername
actorJwt.getSubject() → keycloakId → DB lookup → actorUserId
```

**audit_log writes use default @Transactional** (no qualifier needed — main is @Primary).

### Adding a New Admin Endpoint
1. **Entity** — if reading from an existing DB, the JPA entity already exists in the appropriate `entity/` package (main/crypto/aggregator)
2. **Repository** — add to `repository/{db}/` package; extend `JpaRepository<Entity, IdType>`; use custom `@Query` for complex filters
3. **Service** — create `Admin{Feature}Service` in `service/`; handle business logic + cross-DB calls; specify transaction manager if writing to crypto/aggregator
4. **DTO** — create response DTO in `dto/`; service maps entity → DTO before returning
5. **Controller** — add `@RestController` to `Admin{Feature}Controller`; all endpoints require `@PreAuthorize("hasRole('ADMIN')")` + `@RequestMapping("/api/admin/..."); pass DTO to response

### Adding a New Token/Asset Feature
- Token data lives in `crypto_data.asset` table
- If syncing with external sources, use `AggregatorClient` to call `fm-crypto-aggregator` endpoints
- Calls go to the aggregator's `/internal/v1/**` with `X-Internal-Secret` (same `fm-admin.internal-secret`) plus `X-Service-Name: fm-admin` (see `AggregatorClient.internalHeaders()`). The aggregator has NO public write endpoints any more; never call `/api/v1/discovery/*` (removed).
- Always handle source unavailability gracefully — external services may be down

### News Mirroring Pattern
When writing to `admin_news` in main DB:
1. Service saves to `AdminNewsRepository` (main DB) in primary transaction
2. `AdminNewsService.mirrorToAggregator()` is called asynchronously or best-effort
3. If mirroring fails, log and continue — do NOT rollback the admin write
4. The FinMates news source (ID 1) in aggregator DB is the target for all mirrored articles

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

Connects to TWO databases (main + crypto_data). The crypto datasource was removed 2026-11-21 (virtual trading tables dropped by finmates-crypto V27):

| Datasource | DB | Ownership | Tables used | DDL Mode |
|------------|-----|-----------|-------------|----------|
| `mainDataSource` (@Primary) | `main` (finmates-main) | Read-only users; Read-write news + audit_log | `users`, `admin_news`, `audit_log` | `ddl-auto=update` (dev) / `validate` (k8s) |
| `aggregatorDataSource` | `crypto_data` (fm-crypto-aggregator) | Read-write (owns asset + news tables) | `asset`, `news_article`, `news_source`, `asset_price_*` | `ddl-auto=none` |

### Database conventions

- **Main datasource uses `ddl-auto=update` in dev**, matching finmates-main's pattern. Set per-EMF in `DataSourceConfig.mainEntityManagerFactory` via the builder's `properties()` map (NOT via `spring.jpa.hibernate.ddl-auto`, since auto-config is excluded). Driven off `${spring.profiles.active:default}` — `dev` → `update`, anything else → `validate`.
- **Hibernate-only tables on the main DB** (currently `audit_log`): no Flyway migration. Schema is auto-managed by Hibernate on dev startup; production deploys rely on the same DDL having run in dev first (then `validate` confirms parity in k8s).
- **Indexes on Hibernate-managed tables** must be declared via `@Index` annotations on `@Table(indexes = { ... })` so Hibernate creates them alongside the table. See `AuditLog.java` for the canonical example (`idx_audit_log_created_at`, `idx_audit_log_actor`, `idx_audit_log_target`).
- **JSONB columns** must use `@JdbcTypeCode(SqlTypes.JSON)` on the entity field; map them to `Map<String, Object>` rather than `String`. Plain `String` binds as varchar and PostgreSQL rejects it on a `jsonb` column.
- **Crypto and aggregator datasources stay at `ddl-auto=none`** — their schemas are owned by finmates-crypto (Flyway) and fm-crypto-aggregator (Flyway) respectively. Do NOT enable Hibernate DDL on those EMFs from this service.

**Flyway is DISABLED** in fm-admin — this service never runs migrations. The `admin_news` table in `main` DB was created by a direct SQL execute (V11__admin_news.sql from finmates-main). When deploying to a new environment, ensure all three databases exist and the `admin_news` table in `main` is created via:
```bash
psql -h <host> -U postgres -d main -f finmates-main/src/main/resources/db/migration/V11__admin_news.sql
```
All other schemas are managed by their owning services (finmates-main for users, finmates-crypto for portfolios, fm-crypto-aggregator for assets/prices/news).

## Triple Datasource Architecture

Both `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` are excluded in `application.properties`.
All JPA infrastructure is configured manually across three config classes:

**Main & Crypto Datasources** (`DataSourceConfig.java`):
- `mainDataSource` / `mainEntityManagerFactory` / `mainTransactionManager` — marked `@Primary`
- `cryptoDataSource` / `cryptoEntityManagerFactory` / `cryptoTransactionManager` — non-primary
- Entity scan packages: `entity.main` → main DB, `entity.crypto` → crypto DB
- Repository scan packages: `repository.main` → main, `repository.crypto` → crypto (via `MainRepositoryConfig` / `CryptoRepositoryConfig`)
- Naming strategy: `CamelCaseToUnderscoresNamingStrategy` — `firstName` → `first_name`
- DDL: main EMF overrides to `update` (dev) / `validate` (other) via `properties()` on the builder. Crypto and aggregator EMFs inherit the builder default `none`.

**Aggregator Datasource** (`AggregatorRepositoryConfig.java`):
- `aggregatorDataSource` / `aggregatorEntityManagerFactory` / `aggregatorTransactionManager`
- Entity scan package: `entity.aggregator` → crypto_data DB
- Repository scan package: `repository.aggregator`
- Manages `AdminAsset`, `AggregatorNewsSource`, `AggregatorNewsArticle`, `AggregatorSourceTickerConfig`

**Transaction management:**
- Read-only methods (no `@Transactional`) → default to `mainTransactionManager` (@Primary)
- Crypto DB writes (portfolio/position deletes) → `@Transactional("cryptoTransactionManager")`
- Aggregator writes (token/asset/news mirroring) → `@Transactional("aggregatorTransactionManager")`
- News CRUD (main DB writes) → no explicit TM needed (uses @Primary)

## Security

All `/api/admin/**` endpoints require `ROLE_ADMIN` JWT claim (extracted by `jwtAuthConverter()` in `SecurityConfig`). Regular users receive 403.

Public paths: `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`, `/ui/**`

## API Endpoints

### User Management
```
GET    /api/admin/users                   paginated user list (?search=)
GET    /api/admin/users/{id}              user detail
PUT    /api/admin/users/{id}/enabled      toggle isActive { "enabled": true/false }
DELETE /api/admin/users/{id}              soft delete (sets deleted_at, isActive=false)
PUT    /api/admin/users/{id}/password     reset user password via Keycloak { "password": "..." }
```

### Portfolio & Trade Management
Removed 2026-11-21 with virtual trading (finmates-crypto V27 dropped the tables).

### News Management (mirrored to public feed)
```
GET    /api/admin/news                    news articles (newest first)
POST   /api/admin/news                    create article (mirrors to crypto_data)
PUT    /api/admin/news/{id}               update article (mirrors to crypto_data)
DELETE /api/admin/news/{id}              delete article (deletes from crypto_data)
```

### Token & Asset Management
```
GET    /api/admin/tokens                  paginated token list (?search= &sourceId=)
GET    /api/admin/tokens/stats            aggregate counts: { totalTokens, activeTokens } — use for stat panels, not data?.totalElements / page filtering
GET    /api/admin/tokens/{symbol}         token detail with price/volume data
POST   /api/admin/tokens                  create new token { "symbol", "name", "rank" }
PUT    /api/admin/tokens/{symbol}         update token metadata
PATCH  /api/admin/tokens/{symbol}/active  toggle active status
DELETE /api/admin/tokens/{symbol}         delete token
POST   /api/admin/tokens/metadata/fetch   fetch metadata from CoinPaprika for all tokens

GET    /api/admin/tokens/discovery/sources  available source data providers
GET    /api/admin/tokens/discovery/available  tokens available on connected sources
POST   /api/admin/tokens/discovery/add    add discovered token to platform (?symbol= &name= &rank=)
POST   /api/admin/tokens/discovery/add/bulk  bulk add tokens { "symbols": [...] }
POST   /api/admin/tokens/auto-enable-tickers?discover=false  enable all source_ticker_config rows for active assets; discover=true seeds first (30+ s)
```

### Token status model (computed — `TokenStatusService`)
Each token has ONE computed status instead of three independent flags:
`NOT_TRACKED` (asset.is_active off) → `NO_FEED` (tracked, no enabled source_ticker_config row) → `UNKNOWN`
(aggregator unreachable) → `STALE` (no price, or aggregator feedStatus STALE) → `DEGRADED` (aggregator DEGRADED)
→ `LIVE`. Inputs: is_active, enabled feeds, and the aggregator's `GET /api/v1/prices/current` (cached 5 s).
```
GET    /api/admin/tokens?status=NO_FEED,STALE   list filter by computed status (cut in memory, ~1k rows)
GET    /api/admin/tokens/overview                counts per status, REAL source connection state,
                                                 duplicatePairs (one exchange pair enabled for 2+ tokens),
                                                 notTrackedWithFeeds
PATCH  /api/admin/tokens/{symbol}/tracking       {"tracked": bool}; ON with no enabled feed auto-enables every
                                                 exchange that lists it (returned in autoEnabledFeeds)
```
Every token/feed change calls `AggregatorReloader.reloadAfterCommit()` — DEBOUNCED (2 s, `aggregator.reload-debounce-ms`: bulk feed toggles coalesce into one reload) and fired AFTER the transaction
commits (it used to fire before commit, so the aggregator could read old rows). The aggregator applies it without a
restart (sources resubscribe, deactivated symbols are dropped).

### Token Source & Ticker Configuration
```
GET    /api/admin/tokens/sources/health      REAL source connection status from the aggregator (was hardcoded 5x connected)
GET    /api/admin/tokens/sources             ticker mappings for all sources, grouped by sourceId
GET    /api/admin/tokens/sources/{sourceId}  ticker configs for one source (SourceTickerConfig[])
POST   /api/admin/tokens/sources/discover    trigger async token discovery (returns 202)
GET    /api/admin/tokens/sources/discover/status  discovery job status (poll at 2s while running)
PATCH  /api/admin/tokens/sources/{sourceId}/{symbol}/enabled  toggle ticker enabled { "enabled": bool }
GET    /api/admin/tokens/sources/available   available tokens from a source (?sourceId= &onlyNew=)
```

### Platform Statistics
```
GET    /api/admin/stats                   platform-wide counts: totalUsers, activeUsers (not deleted), deletedUsers,
                                          newUsersToday / ThisWeek / Last7Days, loggedInLast7Days, news, tokens.
                                          loggedInLast7Days is a native count on users.last_login (column deliberately
                                          NOT mapped on AdminUser: timestamp without tz, owned by finmates-main)
```

### User Ban Management (Prompt 6b — proxies to finmates-main /api/internal/users/*)
```
POST   /api/admin/users/{id}/ban          ban user { "banType": "SUSPENSION|PERMANENT", "durationDays": N, "reason": "..." }
POST   /api/admin/users/{id}/unban        lift active ban; records USER_UNBANNED audit
GET    /api/admin/users/{id}/ban-history  all bans for user (newest first)
GET    /api/admin/users/{id}/ban-status   current ban status { banned, banType, expiresAt }
```

### Moderation — Reports (Prompt 6b — proxies to fm-social /api/internal/reports/*)
```
GET    /api/admin/reports                 list reports (?status= &reason= &page= &size=) — pass-through from fm-social
GET    /api/admin/reports/{id}            single report detail
POST   /api/admin/reports/{id}/resolve    orchestrate resolution: action=REMOVE_POST|REMOVE_COMMENT|BAN_USER|DISMISS
                                          { "action", "notes", "banDurationDays", "banType" }
POST   /api/admin/reports/{id}/dismiss    shortcut dismiss { "notes" }
```

### Moderation — Direct Content (Prompt 6b — proxies to fm-social /api/internal/posts|comments/*)
```
POST   /api/admin/content/posts/{id}/remove     soft-remove post { "reason" } + audit POST_REMOVED
POST   /api/admin/content/posts/{id}/restore    restore post + audit POST_RESTORED
POST   /api/admin/content/comments/{id}/remove  soft-remove comment { "reason" } + audit COMMENT_REMOVED
POST   /api/admin/content/comments/{id}/restore restore comment + audit COMMENT_RESTORED
```

### Audit Log (Prompt 6b — reads main DB audit_log table)
```
GET    /api/admin/audit                   paginated audit log (?action= &targetType= &targetId= &actorUserId= &startDate= &endDate=)
GET    /api/admin/audit/user/{userId}     all audit entries where user is actor OR subject
```

## Token & Asset Management

### Token Lifecycle (Aggregator DB)
Tokens are stored in `crypto_data.asset` table and managed via `/api/admin/tokens/`:
- **Create** — `POST /api/admin/tokens` adds a new `AdminAsset` with `isActive=true`
- **Update** — `PUT /api/admin/tokens/{symbol}` modifies metadata (name, rank, etc.)
- **Delete** — `DELETE /api/admin/tokens/{symbol}` sets `isActive=false` (soft delete)
- **Discover** — `POST /api/admin/tokens/discovery/add` pulls tokens from connected sources (`fm-crypto-aggregator`)

### Token Source & Ticker Configuration
Tracks how each data source names/maps tokens via `AggregatorSourceTickerConfig`:
- Each source has priority (1–5) — Hyperliquid highest, Gemini lowest
- Ticker mappings stored as symbol → source-specific ticker (e.g., "ETH" → "ETHUSDT" on Binance)
- `/api/admin/tokens/sources` endpoints manage and discover new mappings

### Discovery via Aggregator Client
`AggregatorClient` makes internal HTTP calls to `fm-crypto-aggregator` (configured via `aggregator.url`):
```java
// Source status:    GET  http://localhost:8088/api/v1/prices/sources/status   (public, read-only)
// Discovery:        GET  /internal/v1/discovery/available|tokens|tokens/all
//                   POST /internal/v1/discovery/seed|reload-assets
// Metadata:         POST /internal/v1/assets/metadata/fetch
// /internal calls send X-Internal-Secret (INTERNAL_SHARED_SECRET) — the aggregator's gate checks it.
```

## Admin News Mirroring to Public Feed

When an admin creates/updates/deletes news via `/api/admin/news`:
1. Changes persist to `main.admin_news` (finmates-main DB)
2. `AdminNewsService` **mirrors** the changes to `crypto_data.news_article` (best-effort, non-blocking)
   - Uses `AggregatorNewsSourceRepository` + `AggregatorNewsArticleRepository` (JPA)
   - "FinMates" source (tier=0) seeded by V5 migration in fm-crypto-aggregator
   - Admin article ID stored as `external_id` for deduplication
   - Failures logged but don't rollback the admin DB write
3. Frontend fetches from `/api/news` (fm-crypto-data), which reads both RSS + mirrored admin articles
4. Detail page shows `content` field + link to original URL

**Important:** Do NOT modify `news_article` or `news_source` tables directly in `crypto_data` DB. All admin news goes through this mirroring flow.

## Entity Notes

### Main DB Entities
- `AdminUser.isActive` → `is_active` column in `users` table; `isActive=false` + `deletedAt=now()` = soft delete
- `AdminNewsArticle.symbols` — stored as comma-separated string ("BTC,ETH"); parsed to `List<String>` in `AdminNewsService`
- `AdminNewsArticle.content` — custom editorial summary (not copied from external sources); mirrored to `news_article.content`

### Crypto DB Entities
- `AdminPortfolio.type` / `.provider` — stored as VARCHAR in DB (EnumType.STRING in finmates-crypto)
- `AdminTrade.side` (BUY|SELL) and `.status` (OPEN|CLOSED|CANCELLED) — VARCHAR in DB

### Aggregator DB Entities
- `AdminAsset` — represents a tracked token (symbol, name, rank); `isActive=false` for soft deletes
- `AggregatorSourceTickerConfig` — maps canonical symbol ("ETH") to source-specific ticker ("ETHUSDT", "ETHUSD", etc.)
  - Primary key: (sourceId, symbol)
  - Allows each data source to use different naming conventions
- `AggregatorNewsSource` / `AggregatorNewsArticle` — mirrors of public news feed; admin articles stored with `source_id=1` ("FinMates")

## Keycloak Integration

### Role Extraction (JWT)
`SecurityConfig.jwtAuthConverter()` extracts roles from **both** Keycloak JWT scopes:
- **Realm-level roles** (`realm_access.roles`) — e.g., `["admin"]` → `ROLE_ADMIN`
- **Client-level roles** (`resource_access.<clientId>.roles`) — searched across all clients

Both are uppercased and prefixed with `ROLE_` so `@PreAuthorize("hasRole('ADMIN')")` works correctly. A user needs either realm-level `admin` role OR client-level `admin` role in any client.

### Admin Client (Password Reset)
`AdminKeycloakService` uses `KeycloakAdminProvider` to connect via Keycloak admin-cli client. **Always use `master` realm + `admin-cli` client for admin operations** — using the `finmates` realm returns 401. Configuration in `application.properties`:
```
keycloak.auth-server-url=http://localhost:8180       # dev, or https://auth.finmates.com (prod)
keycloak.realm=finmates                              # target realm for operations (admin uses master internally)
keycloak.admin.username=admin
keycloak.admin.password=${KEYCLOAK_ADMIN_PASSWORD}
```
**TLS (fixed):** `KeycloakAdminProvider` carries the Keycloak ADMIN username/password, so it verifies the certificate chain AND host name in **every profile, `dev` included** (the former trust-all `resteasyClient()` is gone; there is no dev exception for credential-carrying clients). It uses the default JVM truststore, which includes the pinned in-cluster Keycloak self-signed cert imported by the Dockerfile (see Certificate facts below; the public AlphaSSL wildcard is external-ingress only). Escape hatch: the same `finmates.security.jwks.trust-store` / `-password` / `-type` properties as the JWKS fetch (see below); `KeycloakAdminProvider.init()` (`@PostConstruct`) loads it via `JwksTlsSupport.customTrustStoreSslContext(...)` and a set-but-unreadable path fails the boot. Built by `verifiedResteasyClient(SSLContext)` (null = default truststore; never sets a `hostnameVerifier`). Test: `KeycloakAdminProviderTlsTest` (local HTTPS server with a self-signed cert, driven through `newKeycloakAdminClient()`: rejected by the default truststore before any credentials are sent, accepted only via the additional (pinned) truststore, a different self-signed cert stays rejected, rejected on host name mismatch). Local dev against `https://auth.finmates.com` externally uses the public chain; a local Keycloak with a self-signed cert is added via the (additive) truststore properties.

## JWKS TLS verification (JwtDecoder)

The `JwtDecoder` fetches the Keycloak JWKS (`https://auth.finmates.com/realms/finmates/...`). A forged JWKS would let an attacker mint tokens for any user, so TLS is verified on that fetch:

- **Every profile except `dev`** (`@Profile("!dev")` bean `jwtDecoder`): normal TLS verification (certificate chain + host name) using the default JVM truststore (which includes the pinned in-cluster Keycloak cert, see Certificate facts). No trust-all anywhere on this path.
- **Optional custom truststore** (escape hatch; in-cluster the pinned cert is already in the image's cacerts): `finmates.security.jwks.trust-store` (file path; env `FINMATES_SECURITY_JWKS_TRUST_STORE`), `finmates.security.jwks.trust-store-password` (env `FINMATES_SECURITY_JWKS_TRUST_STORE_PASSWORD`), `finmates.security.jwks.trust-store-type` (default `PKCS12`). When set it is ADDITIVE: its certificate entries are added to the default JVM trust anchors (never only that store) for the JWKS fetch AND the Keycloak admin client (`KeycloakAdminProvider`); chain and host name are still verified (this is NOT trust-all). A set-but-unreadable path fails the boot on purpose. Unset = default JVM truststore (incl. the pinned cert).
- **`dev` profile only** (`@Profile("dev")` bean `devTrustAllJwtDecoder`): trust-all + no host name check, for a local Keycloak with a self-signed cert; logs a loud `*** INSECURE ***` WARN at startup.
- Code: `JwksTlsSupport` (RestTemplate factories) + the static `verifiedJwtDecoder(...)` / `trustAllJwtDecoder(...)` factories in the security config; both decoders keep the issuer + timestamp + azp validators. Tests: `JwksTlsVerificationTest` (local HTTPS JWKS server with a self-signed cert: rejected by the default truststore, accepted only via a custom truststore, rejected on host name mismatch).
- **Certificate facts (in-cluster path):** pods resolving `auth.finmates.com` (CoreDNS rewrite) reach Keycloak's OWN TLS port, which serves a **self-signed** certificate: subject `CN=keycloak, CN=auth.finmates.com, O=Finmates Inc`, SAN `keycloak` + `auth.finmates.com`, valid 2024-09-23 -> **2034-09-21**, RSA 2048, SHA-256 `C2:38:AA:4F:9B:D7:92:AA:1A:85:EF:B2:17:69:1C:2E:82:30:76:53:6D:24:58:76:A7:05:18:4E:C0:99:D4:A0`. It is **pinned**: the `Dockerfile` copies `cert/self-signed/finmates.cert` to `/opt/finmates/finmates.cert` and runs `keytool -importcert -noprompt -trustcacerts -alias finmates-keycloak -cacerts` (ADDS to the default cacerts; this is the intended mechanism, NOT a legacy artifact). Effective trust = default JVM cacerts + exactly this cert; hostname verification stays on; **never trust-all** outside `dev`. The public GlobalSign AlphaSSL `*.finmates.com` wildcard (expires **2026-12-02**) exists only on the EXTERNAL ingress. Design + canonical cert: `docs/adr/keycloak-tls-pin/` in the monorepo root.
- **A missing intermediate in the ingress TLS secret** breaks Java clients that use the EXTERNAL ingress path (browsers hide it by fetching the intermediate; Java does not -> `PKIX path building failed`). This concerns external clients only; in-cluster traffic uses the pinned self-signed cert above. Fix the full chain (leaf + AlphaSSL intermediate) in the TLS secret - never trust-all.
- **Startup log + rotation:** `KeycloakTrustAnchorLogger` logs `Keycloak pinned certificate: subject=... notAfter=... sha256=... trustedByJvm=true` on startup (WARN `NOT trusted by the JVM truststore` if the image lacks the import or <60 days to expiry; ERROR on fingerprint MISMATCH or expiry). Properties: `finmates.security.keycloak.pinned-cert-path` (default `/opt/finmates/finmates.cert`), `finmates.security.keycloak.pinned-cert-sha256`. **Rotation:** when Keycloak's cert changes, take the new fingerprint, update `cert/self-signed/finmates.cert` in ALL service repos (and the expected SHA-256 in `KeycloakTrustAnchorLoggerTest` / the logger default), rebuild the images. Later infra follow-up: point the CoreDNS rewrite at the ingress controller so pods see the public chain, then drop the pin. Known weakness: the Keycloak private key is tracked in finmates-keycloak (`cert/self-signed/finmates.key.pem`) - the cert needs rotating, key delivered via a K8s Secret.
- Background: trust-all was added in the initial JWKS decoder commits (April 2026) and later replaced by verified TLS. The self-signed `cert/self-signed/finmates.cert` is what in-cluster pods really see (the earlier belief that the public wildcard was served in-cluster was wrong), so it is now deliberately pinned instead of trusting everything.

## JWT azp guard

`com.finmates.admin.security.AzpAllowlistValidator` rejects access tokens whose `azp` (authorized party) claim is not on an allowlist, so tokens minted for third-party / dynamically registered (DCR / MCP) Keycloak clients cannot be replayed against this API. Canonical source and contract: `docs/adr/azp-guard` in the monorepo root (copy verbatim; only the package line differs).

- Properties: `finmates.security.azp.allowed` (comma list, env `FINMATES_SECURITY_AZP_ALLOWED`, default `finmates-app`) and `finmates.security.azp.enforce` (env `FINMATES_SECURITY_AZP_ENFORCE`, default `true`). Defaults live in the base config.
- Wiring: `SecurityConfig.withTokenValidators(...)` wraps `JwtValidators.createDefaultWithIssuer(issuerUri)` (issuer + timestamps, unchanged) and `AzpAllowlistValidator` in a `DelegatingOAuth2TokenValidator`. The non-dev `jwtDecoder()` and the dev-only `devTrustAllJwtDecoder()` beans both go through it (see "JWKS TLS verification"). A rejection is an `InvalidBearerTokenException`, i.e. the normal 401 `invalid_token`.
- Missing `azp` is rejected when enforcing. Spring validates any presented Bearer token even on `permitAll` paths, so a disallowed-azp token also gets 401 on public endpoints.
- Report-only logging (rev 2): `AzpAllowlistValidator` is silent; `com.finmates.admin.security.AzpReportOnlyFilter` (registered by `SecurityConfig.azpReportOnlyFilter`, `FilterRegistrationBean` order -50, after Spring Security) logs one `azp guard (report-only)` WARN per distinct (azp, method, endpoint) with the service name (`spring.application.name`), method, a normalised endpoint (max 3 segments, ids -> `{id}`, no query string) and the sanitised azp - never the token. It is a no-op when enforcing. `AzpReportSupport` does the normalising/de-duplication. Allowlist/enforce come from the same `finmates.security.azp.*` properties the decoder uses. Tests: `AzpReportSupportTest`, `AzpReportOnlyFilterTest`.
- K8s: `deployment.yaml` sets `FINMATES_SECURITY_AZP_ENFORCE: "false"` (report-only) for the first rollout; no profile properties file overrides it. Jenkins applies it (`kubectl apply -f deployment.yaml`, with `|| true`, so an apply failure is silent). After reviewing the report-only log lines, flip it to `"true"` (or delete the entry) in a later commit.
- Rollout: first deploy with `FINMATES_SECURITY_AZP_ENFORCE=false` (report-only; see the report-only logging bullet), watch logs, then remove the override. Keycloak DCR must stay closed until every service enforces.
- Tests: `AzpAllowlistValidatorTest`, `SecurityConfigAzpWiringTest` (run with `-Dtest=AzpAllowlistValidatorTest,SecurityConfigAzpWiringTest`).

## Known Gotchas

- **`source_ticker_config.symbol` is mixed-case** — Hyperliquid stores perpetual k-tokens as lowercase-k (`kSHIB`, `kDOGS`, `kFLOKI`). The `asset` table always stores symbols uppercase. When toggling a ticker, always use case-insensitive lookup: `findBySourceIdAndSymbolIgnoreCase`. When joining ticker symbols to the asset table (e.g., for source filter), call `.toUpperCase()` on the ticker symbol before matching. **Never** use `symbol.toUpperCase()` before the ticker lookup itself — it converts `kSHIB` → `KSHIB` which won't find the DB row.

- **Derived Spring Data methods with `boolean` + `@Column(name="is_enabled")`** — `findByEnabledTrue()` on a field declared as `boolean enabled` with `@Column(name = "is_enabled")` can behave unexpectedly because Lombok generates `isEnabled()` as the getter. Always use an explicit `@Query("SELECT t FROM ... WHERE t.enabled = true")` for filtering on this field (see `findEnabledBySourceId` in `AggregatorSourceTickerConfigRepository`).

- **`GET /api/admin/tokens` now accepts `?sourceId=`** — server-side filter returning only assets whose symbol matches an enabled ticker for that source. The filter fetches enabled symbols via `findEnabledBySourceId(sourceId)`, normalises to uppercase, then queries `findBySymbolIn(symbols, pageable)`. Previously this was client-side only (filtered just the current page of 20 — incorrect totals).

- **`getAvailableTokens()` `isAsset` was hardcoded `false`** — Fixed: now fetches all asset symbols from the `asset` table and checks membership. Requires no backend call — reads from the same `aggregatorDataSource`. Visible effect: Browse page "In Platform" stat now shows the correct count instead of always 0.

- **fm-admin has no WebFlux dependency — use RestTemplate, not WebClient** — `pom.xml` only has `spring-boot-starter-web`. `FmSocialClient` and `FmMainClient` follow the same RestTemplate pattern as `AggregatorClient`. Adding `spring-boot-starter-webflux` would conflict with the existing MVC setup. Do NOT add it unless WebFlux is explicitly needed.

- **Inter-service auth: shared secret** — `FmSocialClient` and `FmMainClient` authenticate to fm-social / finmates-main `/api/internal/**` endpoints via the `X-Internal-Secret` header (NOT a JWT — there is no token relay; the inbound admin JWT is not forwarded). Source value: `${fm-admin.internal-secret}` ← `${INTERNAL_SHARED_SECRET:dev-local-secret}`. In k8s, the env var is injected from the `fm-internal-secret` Secret. Both fm-admin and fm-social/finmates-main MUST be configured with the same `INTERNAL_SHARED_SECRET` in non-dev profiles. Dev profile falls back to `dev-local-secret` on all sides.
  - **Fail-fast at startup:** `InternalSecretValidator` (`com.finmates.admin.config`) runs in `@PostConstruct` and throws `IllegalStateException` if `fm-admin.internal-secret` is blank or equals `dev-local-secret` while the active profile is one of `k8s`, `prod`, `production`, `aws`. Spring Boot will refuse to start.
  - **Startup log:** on every successful startup the validator logs `Internal shared secret configured (length=N, profile=X)` at INFO. `FmSocialClient` constructor also logs `FmSocialClient initialized: baseUrl=…, internalSecretLength=N`. The secret value is NEVER logged — only its length.
  - **Debugging 401s on `/api/internal/**`:** check both services' startup logs for the `Internal shared secret configured (length=N)` line and confirm the lengths match between fm-admin and fm-social/finmates-main. fm-social's filter now also returns `"hint":"X-Internal-Secret missing or mismatched"` in the 401 body and logs a WARN with path + remote address (no values) on every rejection.
  - **Resolution actions sent to fm-social** must use values from `com.finmates.admin.client.dto.ResolutionAction` (mirror of fm-social's enum): `USER_WARNED`, `CONTENT_REMOVED`, `NO_ACTION`, `USER_SUSPENDED`, `USER_BANNED`. The admin UI's `Dismiss` action maps to `NO_ACTION`; `REMOVE_POST` / `REMOVE_COMMENT` map to `CONTENT_REMOVED`; `BAN_USER` maps to `USER_BANNED`. `FmSocialClient.resolveReport` is typed on the enum so a wrong literal can't compile, and on a 4xx/5xx from fm-social it throws `FmSocialIntegrationException` (extends `ResponseStatusException`) — Spring MVC propagates the upstream status to the admin response so frontend toasts surface the real reason instead of "Failed to dismiss report".

- **`audit_log` table must exist before fm-admin writes audit entries** — The table is created by finmates-main V15 migration on its next startup. fm-admin's `AuditLog` JPA entity uses `ddl-auto=none` — it will NOT create the table. If fm-admin throws `Table 'main.audit_log' doesn't exist`, restart finmates-main first.

- **HikariCP requires `jdbc-url`, not `url`** — when using custom `@ConfigurationProperties` prefix (e.g. `datasource.main.*`), HikariCP does not perform Spring Boot's auto-mapping of `url` → `jdbcUrl`. Always use `jdbc-url` in property files. The k8s profile has a bug: uses `url` instead of `jdbc-url` — fix before deploying to Kubernetes, or service will fail with `IllegalArgumentException: jdbcUrl is required with driverClassName`.

- **Nullable `is_active` column in users table** — the `is_active` column in the main DB's `users` table can be NULL for legacy records. `AdminUser.isActive` is a `Boolean` wrapper (not primitive `boolean`) to allow null. When mapping to DTOs, always null-check: `dto.setEnabled(u.getIsActive() != null ? u.getIsActive() : false)` to avoid `NullPointerException` on unboxing.

- **Admin dashboard 403 on 2026-11-21 = dropped tables.** `GET /api/admin/stats` ran `select count(*) from portfolios`
  after finmates-crypto V27 dropped virtual trading; the crash surfaced as 403 (see next item). When another service drops
  or renames a table fm-admin reads, remove or remap the entity here in the same release.
- **(Fixed 2026-11-21: `/error` is now permitAll, so server errors return 500.)** Historical: **A 403 on `/api/admin/**` could mask a 500** — `SecurityConfig` ends with `.anyRequest().denyAll()`. When a controller throws an unhandled exception (e.g., Hibernate `column does not exist` after a schema migration in another service), Spring forwards to `/error`, which doesn't match any `permitAll`/`hasRole` rule and is denied → response is **403**, not 500. If admin endpoints suddenly start 403'ing for a confirmed admin user, check fm-admin server logs for SQL/Hibernate errors before assuming a security misconfig. Root cause once: `AdminPortfolio` still mapped `cash_balance`/`initial_balance` after V10 of finmates-crypto dropped them in the shared-wallet refactor — fixed by removing those columns from `AdminPortfolio`, `AdminPortfolioDto`, `AdminPortfolioSummaryDto`, and the corresponding service mappers; `resetPortfolio` now only deletes positions (cash reset is owned by finmates-main's wallet endpoint).

- **Keycloak cert / JWKS TLS** - the JwtDecoder verifies TLS outside `dev` (see "JWKS TLS verification (JwtDecoder)"). Symptom if the pod cannot validate the cert: `JwtDecoderInitializationException` / `PKIX path building failed` / 401 on every request. In-cluster first check the startup line `Keycloak pinned certificate: ... trustedByJvm=true` (WARN `NOT trusted` = the image lacks the Dockerfile cacerts import; ERROR `MISMATCH` = Keycloak's cert rotated, update `cert/self-signed/finmates.cert` and rebuild). For EXTERNAL clients a missing intermediate in the ingress TLS secret is the usual cause. Fix the trust, never trust-all. The optional `finmates.security.jwks.trust-store*` is only an ADDITIVE escape hatch. Only the `dev` profile uses a trust-all decoder.

- **Three datasource initialization order** — `@Primary` on `mainDataSource` means that if a bean can auto-wire a datasource, it gets main. For crypto/aggregator-only code, explicitly specify the transaction manager: `@Transactional("cryptoTransactionManager")` or `@Transactional("aggregatorTransactionManager")`. Omitting this for aggregator writes will silently use main DB and fail.

## Package Layout

```
config/
  DataSourceConfig.java              — main + crypto datasources, EntityManagerFactory, TransactionManager
  AggregatorRepositoryConfig.java    — @EnableJpaRepositories for aggregator DB
  MainRepositoryConfig.java          — @EnableJpaRepositories for main DB
  CryptoRepositoryConfig.java        — @EnableJpaRepositories for crypto DB
  SecurityConfig.java                — JWT auth, CORS, role extraction from realm_access/resource_access
  KeycloakAdminProvider.java         — Keycloak admin client for password resets
  SchemaInitializer.java             — Initialize FinMates news source on startup

entity/
  main/        AdminUser, AdminNewsArticle, AuditLog (+ AuditAction, AuditTargetType enums)
  crypto/      AdminPortfolio, AdminTrade, AdminPosition
  aggregator/  AdminAsset, AggregatorNewsSource, AggregatorNewsArticle, AggregatorSourceTickerConfig

repository/
  main/        AdminUserRepository (+ findByKeycloakId), AdminNewsRepository, AuditLogRepository
  crypto/      AdminPortfolioRepository, AdminTradeRepository, AdminPositionRepository
  aggregator/  AdminAssetRepository, AggregatorNewsSourceRepository, AggregatorNewsArticleRepository,
               AggregatorSourceTickerConfigRepository

client/
  FmSocialClient   — RestTemplate client for fm-social /api/internal/** (X-Internal-Secret header)
  FmMainClient     — RestTemplate client for finmates-main /api/internal/** (X-Internal-Secret header)

service/
  Admin*Service        — Business logic for users, portfolios, trades, positions, news, stats
  AdminModerationService — Orchestrates report resolution, content removal, user bans + writes audit
  AuditLogService      — Writes + queries audit_log (main DB, @Primary TM, Specification-based search)
  AdminKeycloakService — Password reset via Keycloak admin client
  AdminTokenService    — Token CRUD and discovery
  AdminSourceTickerService  — Source ticker mapping management
  TokenDiscoveryService     — Discover tokens from connected sources
  AdminNewsService     — News CRUD + mirroring to crypto_data DB
  AggregatorClient     — Internal REST client for aggregator service health checks

dto/
  Admin*Dto               — Response DTOs for all entities
  Create*Request          — Request bodies for POST/PUT operations
  PageResponse<T>         — Generic page wrapper (matches fm-social page JSON shape)
  ReportDetailResponse    — Report JSON from fm-social
  ResolveReportRequest, DismissReportRequest — Report resolution inputs
  BanUserRequest, UnbanUserRequest, UserBanResponse, BanStatusResponse — Ban management
  RemoveContentRequest    — Content removal input
  AuditLogResponse        — Audit log response (maps AuditLog entity)
  PostContentResponse, CommentContentResponse — Social content responses
  SourceTickerConfigDto, SourceStatusDto, SourceTokensDto, AvailableTokenDto

monitoring/                — Services dashboard (/api/admin/services/status|db-connections|cluster-alerts):
  AdminServicesController, ServicesStatusService, ClusterAlertsService, PgConnectionService,
  PrometheusClient, ServicesHealthClient, ServicesRegistryConfig, TtlCache,
  dto/ (ServiceStatusDto, DbConnections*, ClusterAlertsResponseDto, ...), registry/ (ServiceDescriptor, HealthType),
  resolver/ (K8sPodServiceResolver: pod IP -> service via the k8s API; degrades to "unresolved" outside a pod)

controller/
  AdminUser*              — CRUD + ban management (ban/unban/ban-history/ban-status)
  AdminReportController   — Report list + resolve + dismiss (proxies to fm-social)
  AdminContentController  — Direct post/comment remove/restore (proxies to fm-social)
  AdminAuditController    — Audit log search and user history
  AdminPortfolio*, AdminTrade*, AdminPosition* — Portfolio/trade endpoints
  AdminToken*, AdminTokenSource*  — Token/asset/source management endpoints
  AdminNews*, AdminStats*         — News + stats endpoints
```


## Codebase Context
This is a monorepo. Before making architectural decisions or cross-service changes, consult:
- `graphify-out/GRAPH_REPORT.md` — codebase graph summary
- `graphify-out/graph.json` — detailed node/edge data

### Key Architecture (from Graphify)
- **God nodes**: UserService, UserRepository, KeycloakAdminClientService, PortfolioController
- **Hyperedges**: 
  - Cross-Service JWT Auth: finmates-main ↔ finmates-crypto ↔ finmates-front via Keycloak
  - Price Pipeline: fm-crypto-aggregator → fm-crypto-data → SSE consumers
  - Admin: Triple datasource (main, crypto, crypto_data DBs)

## Claude Code tooling

This repo is indexed by Graphify at `F:\Projects\graphify-out\`. The post-commit hook (installed via `graphify hook install`) auto-rebuilds the AST graph on every commit — no LLM cost, ~1–3 s.

Before starting complex refactors, query the graph for dependency impact:
```bash
graphify query "<search term>"                       # BFS traversal of graph.json
graphify query "<search term>" --dfs --budget 4000  # DFS with higher token budget
```

Hook management:
```bash
cd F:/Projects/fm-admin
graphify hook status    # verify hook is installed
graphify hook install   # reinstall if missing
```

Full workspace re-index (all 7 services at once, from monorepo root):
```bash
cd F:/Projects && graphify update .
```

## Persistent Context (claude-mem)
Cross-session memory via the `claude-mem` MCP plugin (thedotmack/claude-mem v12.1.0).

**Storage**: `C:/Users/user/.claude-mem/claude-mem.db` (SQLite)
Override path: set `CLAUDE_MEM_DATA_DIR` env var or edit `~/.claude-mem/settings.json`

### When to use it
- **Session start** — search for prior decisions, bugs, or patterns before touching shared code
- **Before cross-service changes** — retrieve past learnings about JWT auth, Keycloak, portfolio flows
- **After fixing a non-obvious bug** — observations are auto-saved by the SessionStart hook; search them next time the same area breaks

### 3-layer search workflow (follow this order — 10x token savings)
```
1. search("topic keyword")          → index of matching IDs (~50-100 tokens each)
2. timeline(anchor=ID)              → surrounding session context for promising hits
3. get_observations([ID1, ID2])     → full detail ONLY for the IDs you actually need
```

### Key tool reference
| Tool | Purpose |
|------|---------|
| `search(query)` | Fast keyword/semantic index lookup — returns IDs + summaries |
| `smart_search(query)` | Broader semantic search across all observations |
| `timeline(anchor=ID)` | Session timeline around a specific observation |
| `get_observations([IDs])` | Fetch full text for specific observation IDs |
| `build_corpus(name, query)` | Build a focused retrieval corpus for a topic |
| `query_corpus(name, query)` | Query a pre-built corpus |

### What is stored
The SessionStart hook auto-records a summary of each session (decisions made, bugs fixed, files changed). Observations are tagged with timestamps and session IDs visible in the session-start context block.