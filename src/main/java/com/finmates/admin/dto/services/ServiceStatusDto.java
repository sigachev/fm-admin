package com.finmates.admin.dto.services;

import java.time.Instant;

/**
 * One row on the services dashboard.
 *
 * <p>Honest degradation:
 * <ul>
 *   <li>{@code healthStatus = UNKNOWN} when the probe is unreachable or returns
 *       401 — never fabricate UP.</li>
 *   <li>{@code dbConnections = null} when either the service has no
 *       {@code dbName} (e.g. redis, keycloak, admin-panel) OR the pod-IP
 *       resolver returned no IPs for this service (Phase 1a no-op default).</li>
 *   <li>{@code source} reports the origin of the counts: {@code PG_STAT_ACTIVITY}
 *       when populated, {@code NONE} otherwise.</li>
 * </ul>
 *
 * @param dbConnections per-service Postgres counts, or {@code null}
 *                      (see class javadoc for when)
 * @param error         human-readable error reason when health probing failed
 *                      (timeout, connection refused, parse error); null on success
 */
public record ServiceStatusDto(
        String name,
        String displayName,
        HealthStatus healthStatus,
        String version,
        Long uptimeSeconds,
        String image,
        DbConnectionCountsDto dbConnections,
        String source,
        Instant lastCheckedAt,
        String error
) {
}
