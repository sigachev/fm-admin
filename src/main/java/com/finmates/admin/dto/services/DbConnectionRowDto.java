package com.finmates.admin.dto.services;

/**
 * One pg_stat_activity grouping row exposed on the cluster-wide DB-connection
 * panel.
 *
 * @param datname         Postgres database name
 * @param clientAddr      client IP (pod IP or LAN IP); null for local/unix-socket
 * @param applicationName libpq application_name (or r2dbc-postgresql, etc.)
 * @param state           Postgres state ('active' / 'idle' / 'idle in transaction')
 * @param conns           number of connections sharing this group
 * @param isPodConnection true iff {@code clientAddr} falls inside the
 *                        {@code 10.244.0.0/16} pod CIDR
 * @param isExternal      true iff {@code clientAddr} is non-null and NOT inside
 *                        the pod CIDR — i.e. a developer laptop or other LAN
 *                        client. The "is a laptop eating connections" signal.
 * @param serviceName     resolved service name for {@code clientAddr} via the
 *                        pod-IP resolver, or null if unresolved (Phase 1a no-op,
 *                        or pod absent from the live k8s API list)
 */
public record DbConnectionRowDto(
        String datname,
        String clientAddr,
        String applicationName,
        String state,
        int conns,
        boolean isPodConnection,
        boolean isExternal,
        String serviceName
) {
}
