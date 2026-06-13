package com.finmates.admin.dto.services;

import java.time.Instant;

/**
 * One firing-or-pending Prometheus alert.
 *
 * <p>{@code severity} is one of {@code "critical" | "warning" | "info" | "none"}.
 * {@code state} is the Prometheus state string ({@code "firing"} or {@code "pending"}).
 * {@code summary} and {@code description} come from the alert's annotations and are
 * passed through verbatim — they may be null when the alert rule omitted them.
 *
 * <p>{@code instance} is the {@code instance} label if present, falling back to
 * {@code node} — the FE displays it as the "where" of the alert.
 */
public record ActiveAlertDto(
        String alertname,
        String severity,
        String state,
        String instance,
        String summary,
        String description,
        Instant activeSince
) {
}
