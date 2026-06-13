package com.finmates.admin.dto.services;

/**
 * Per-node, per-mountpoint disk-usage row for the Cluster Alerts &amp; Storage panel.
 *
 * <p>All fields except {@code node} and {@code mountpoint} may be null when Prometheus
 * returned an incomplete join across the used%/avail/size queries (per ADR-0001 the
 * FE renders missing values as em-dashes rather than zero).
 *
 * <p>{@code node} is the node-exporter {@code instance} label string (e.g.
 * {@code "10.0.0.110:9100"}). Mapping {@code instance} to a Kubernetes node name
 * would require an additional {@code node_uname_info} join and is deferred — v1
 * surfaces the instance as-is.
 */
public record DiskGaugeDto(
        String node,
        String mountpoint,
        Double usedPercent,
        Long availBytes,
        Long sizeBytes
) {
}
