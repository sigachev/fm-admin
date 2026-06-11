package com.finmates.admin.services.resolver;

import java.time.Instant;

/**
 * Minimal metadata for a representative pod of a given service, scoped to what
 * the services dashboard renders today: image (the deployed image reference)
 * and {@code startedAt} (drives the {@code uptimeSeconds} field on
 * {@link com.finmates.admin.dto.services.ServiceStatusDto}).
 *
 * <p>Captured by the live {@link K8sPodServiceResolver} from the pod list
 * response — {@code items[].spec.containers[0].image} and
 * {@code items[].status.startTime} — at no extra API cost. For multi-replica
 * services, the resolver picks one pod (first-seen) as the representative; the
 * image is identical across replicas of a single Deployment, and the
 * representative startTime is a stable-enough "service uptime" for the
 * dashboard's purposes (the dashboard is a glance surface, not an SLA tool).
 */
public record PodMetadata(String image, Instant startedAt) {
}
