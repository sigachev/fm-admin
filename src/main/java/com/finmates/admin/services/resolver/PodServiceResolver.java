package com.finmates.admin.services.resolver;

import java.util.Optional;
import java.util.Set;

/**
 * Maps PostgreSQL {@code client_addr} pod IPs to k8s service names.
 *
 * <p>{@code pg_stat_activity.client_addr} is a pod IP (in the
 * {@code 10.244.0.0/16} CIDR); k8s DNS only exposes the ClusterIP, so a separate
 * lookup is required to attribute connections back to the originating service.
 *
 * <p>Implementations may degrade honestly: if the resolver cannot reach the
 * k8s API (RBAC denied, network blip), {@link #getServiceNameForPodIp(String)}
 * returns {@link Optional#empty()} and the dashboard surfaces the raw IP with
 * a null service name rather than fabricating an attribution.
 *
 * <p>The {@code NoOpPodServiceResolver} ships in Phase 1a; the live k8s-API
 * implementation is added in Phase 1b alongside the pods-reader RBAC.
 */
public interface PodServiceResolver {

    /**
     * @return service name (e.g. {@code "main"}) for the given pod IP, or empty
     *         if the IP is unknown / unresolved
     */
    Optional<String> getServiceNameForPodIp(String podIp);

    /**
     * @return all pod IPs currently labelled with {@code app=<serviceName>},
     *         or an empty set if unknown
     */
    Set<String> getPodIpsForService(String serviceName);
}
