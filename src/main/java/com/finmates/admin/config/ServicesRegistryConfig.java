package com.finmates.admin.config;

import com.finmates.admin.services.registry.HealthType;
import com.finmates.admin.services.registry.ServiceDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Static registry of services rendered on the admin services dashboard.
 *
 * <p>Hard-coded on purpose: the list is small, churns rarely, and lives
 * next to the rest of the wiring. Adding a service is a one-line edit + redeploy.
 *
 * <p>Health URLs use the in-cluster service DNS short name (e.g.
 * {@code http://main/...}) — confirmed Phase 0. {@code admin-panel} self-probe
 * also uses service DNS (container + service port both 80) so the same shape
 * works inside its own pod.
 *
 * <p>{@code dbName} is null for services that do not own a single DB connection
 * group:
 * <ul>
 *   <li>{@code keycloak} — runs against its own embedded/separate DB; the
 *       cluster panel still surfaces its rows by IP.</li>
 *   <li>{@code redis} — not a Postgres client.</li>
 *   <li>{@code admin-panel} — connects to main + crypto + crypto_data; surfacing
 *       a single per-card count would be misleading. The cluster panel still
 *       attributes every one of its rows.</li>
 * </ul>
 *
 * <p><b>crypto-aggregator note:</b> uses an R2DBC pool
 * ({@code application_name=r2dbc-postgresql}) plus a JDBC Flyway connection at
 * startup. The per-service count is the sum of both — this is intentional,
 * not a bug. crypto-data, also on crypto_data DB, is pure JDBC; pod IP cleanly
 * separates the two.
 */
@Configuration
public class ServicesRegistryConfig {

    @Bean
    public List<ServiceDescriptor> serviceDescriptors() {
        return List.of(
                new ServiceDescriptor("main",              "finmates-main",        "main",        "http://main/actuator/health",              HealthType.ACTUATOR,   "main"),
                new ServiceDescriptor("crypto",            "finmates-crypto",      "crypto",      "http://crypto/actuator/health",            HealthType.ACTUATOR,   "crypto"),
                new ServiceDescriptor("crypto-data",       "fm-crypto-data",       "crypto_data", "http://crypto-data/actuator/health",       HealthType.ACTUATOR,   "crypto-data"),
                new ServiceDescriptor("crypto-aggregator", "fm-crypto-aggregator", "crypto_data", "http://crypto-aggregator/actuator/health", HealthType.ACTUATOR,   "crypto-aggregator"),
                new ServiceDescriptor("messaging",         "fm-messaging",         "messaging",   "http://messaging/actuator/health",         HealthType.ACTUATOR,   "messaging"),
                new ServiceDescriptor("social",            "fm-social",            "social",      "http://social/actuator/health",            HealthType.ACTUATOR,   "social"),
                new ServiceDescriptor("trading",           "fm-trading",           "trading",     "http://trading/actuator/health",           HealthType.ACTUATOR,   "trading"),
                new ServiceDescriptor("uniswap",           "fm-uniswap",           "uniswap",     "http://uniswap/actuator/health",           HealthType.ACTUATOR,   "uniswap"),
                new ServiceDescriptor("admin-panel",       "fm-admin (self)",      null,          "http://admin-panel/actuator/health",       HealthType.ACTUATOR,   "admin-panel"),
                new ServiceDescriptor("keycloak",          "keycloak",             null,          "http://keycloak/q/health",                 HealthType.KEYCLOAK_Q, "keycloak"),
                new ServiceDescriptor("redis",             "redis",                null,          null,                                       HealthType.REDIS_PING, "redis")
        );
    }
}
