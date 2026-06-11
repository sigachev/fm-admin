package com.finmates.admin.dto.services;

/**
 * Per-service PostgreSQL connection counts derived from
 * {@code pg_stat_activity}, grouped by the service's resolved pod IPs.
 *
 * <p>Note: there is no {@code max} field — Postgres has no native concept of a
 * client-side pool maximum. The dashboard intentionally shows
 * {@code active / idle / idle-in-transaction}, not {@code active/max}.
 *
 * <p>For {@code crypto-aggregator}, counts include the R2DBC pool
 * ({@code application_name=r2dbc-postgresql}) plus the JDBC Flyway startup
 * connection. This mixing is intentional — see
 * {@link com.finmates.admin.config.ServicesRegistryConfig} javadoc.
 */
public record DbConnectionCountsDto(
        int active,
        int idle,
        int idleInTransaction
) {
}
