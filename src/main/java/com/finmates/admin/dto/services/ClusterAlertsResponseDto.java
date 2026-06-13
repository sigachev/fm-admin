package com.finmates.admin.dto.services;

import java.time.Instant;
import java.util.List;

/**
 * Envelope for {@code GET /api/admin/services/cluster-alerts}.
 *
 * <p>Per ADR-0001 (null-on-uncertainty), when Prometheus is unreachable, blank-URL,
 * or returns an unparseable response, the BE returns HTTP 200 with
 * {@code available=false}, a human-readable {@code reason}, and empty
 * {@code disk}/{@code alerts} lists. The FE renders an explicit "Cluster metrics
 * unavailable" Alert in that branch rather than a fake all-clear.
 *
 * <p>{@code fetchedAt} is the timestamp the response was assembled at (NOT the
 * cache hit time) — it advances each time the underlying Prometheus query runs.
 */
public record ClusterAlertsResponseDto(
        boolean available,
        String reason,
        List<DiskGaugeDto> disk,
        List<ActiveAlertDto> alerts,
        Instant fetchedAt
) {
    public static ClusterAlertsResponseDto unavailable(String reason, Instant fetchedAt) {
        return new ClusterAlertsResponseDto(false, reason, List.of(), List.of(), fetchedAt);
    }
}
