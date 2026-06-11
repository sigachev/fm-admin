package com.finmates.admin.services.registry;

/**
 * Static description of a service rendered on the admin services dashboard.
 *
 * <p>The dashboard is read-only: probe URLs are health-only (never metrics) and
 * connection counts come from {@code pg_stat_activity} attributed via the
 * pod-IP &rarr; service resolver.
 *
 * @param name        canonical short name (matches k8s app label and the service-DNS host)
 * @param displayName human-readable label for the UI
 * @param dbName      PostgreSQL {@code datname} this service connects to, or {@code null}
 *                    if it does not own a DB connection group (e.g. keycloak, redis,
 *                    admin-panel which spans three DBs)
 * @param healthUrl   URL used by {@link com.finmates.admin.client.ServicesHealthClient};
 *                    null when {@link #healthType} == {@link HealthType#NONE} or
 *                    {@link HealthType#REDIS_PING}
 * @param healthType  how to interpret the response
 * @param appLabel    k8s {@code app} label, used by the pod resolver to map pod IPs to
 *                    this service name
 */
public record ServiceDescriptor(
        String name,
        String displayName,
        String dbName,
        String healthUrl,
        HealthType healthType,
        String appLabel
) {
}
