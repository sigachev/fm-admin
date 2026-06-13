package com.finmates.admin.service;

import com.finmates.admin.client.PrometheusClient;
import com.finmates.admin.client.PrometheusClient.AlertEnvelope;
import com.finmates.admin.client.PrometheusClient.InstantSample;
import com.finmates.admin.dto.services.ActiveAlertDto;
import com.finmates.admin.dto.services.ClusterAlertsResponseDto;
import com.finmates.admin.dto.services.DiskGaugeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Composes {@code GET /api/admin/services/cluster-alerts} from three Prometheus
 * instant queries (root-disk used % + avail + size) plus {@code /api/v1/alerts}.
 *
 * <p>Caches the assembled envelope for {@value #CACHE_TTL_SECONDS}&nbsp;s — matches
 * the FE's ~10&nbsp;s refetch cadence so steady-state polling round-trips to
 * Prometheus at most once per interval.
 *
 * <p>Per ADR-0001, any failure (blank URL, HTTP error, timeout, parse failure)
 * produces a {@code available=false} envelope rather than a 5xx — the FE renders
 * an explicit "Cluster metrics unavailable" Alert. Never returns fabricated zero
 * disk usage or a fake "all clear".
 *
 * <p>Sort order on the alerts list: severity descending
 * ({@code critical &gt; warning &gt; info &gt; other}), then {@code activeSince}
 * descending — so a newly-firing critical surfaces above pre-existing criticals.
 */
@Service
public class ClusterAlertsService {

    private static final Logger log = LoggerFactory.getLogger(ClusterAlertsService.class);

    private static final int CACHE_TTL_SECONDS = 10;
    private static final Duration CACHE_TTL = Duration.ofSeconds(CACHE_TTL_SECONDS);

    /**
     * Root-mountpoint used % across all node-exporter targets. The {@code fstype}
     * negative-match drops in-memory filesystems and lxcfs that have no real backing
     * store — they would otherwise show as "100% used" and be alarming noise.
     */
    static final String DISK_USED_PCT_QUERY =
            "(1 - (node_filesystem_avail_bytes{mountpoint=\"/\",fstype!~\"tmpfs|fuse.lxcfs\"} "
                    + "/ node_filesystem_size_bytes{mountpoint=\"/\",fstype!~\"tmpfs|fuse.lxcfs\"})) * 100";
    static final String DISK_AVAIL_QUERY =
            "node_filesystem_avail_bytes{mountpoint=\"/\",fstype!~\"tmpfs|fuse.lxcfs\"}";
    static final String DISK_SIZE_QUERY =
            "node_filesystem_size_bytes{mountpoint=\"/\",fstype!~\"tmpfs|fuse.lxcfs\"}";

    private final PrometheusClient prometheus;
    private final AtomicReference<TimedHolder<ClusterAlertsResponseDto>> cache = new AtomicReference<>();

    public ClusterAlertsService(PrometheusClient prometheus) {
        this.prometheus = prometheus;
    }

    public ClusterAlertsResponseDto getClusterAlerts() {
        TimedHolder<ClusterAlertsResponseDto> cached = cache.get();
        if (cached != null && cached.isFresh()) {
            return cached.value;
        }
        ClusterAlertsResponseDto built = build();
        cache.set(new TimedHolder<>(built, Instant.now().plus(CACHE_TTL)));
        return built;
    }

    private ClusterAlertsResponseDto build() {
        Instant now = Instant.now();
        if (!prometheus.isConfigured()) {
            return ClusterAlertsResponseDto.unavailable("prometheus.url is not configured", now);
        }
        try {
            List<InstantSample> used = prometheus.queryInstant(DISK_USED_PCT_QUERY);
            List<InstantSample> avail = prometheus.queryInstant(DISK_AVAIL_QUERY);
            List<InstantSample> size = prometheus.queryInstant(DISK_SIZE_QUERY);
            List<AlertEnvelope> raw = prometheus.getAlerts();

            List<DiskGaugeDto> disk = joinDisk(used, avail, size);
            List<ActiveAlertDto> alerts = mapAndSortAlerts(raw);
            return new ClusterAlertsResponseDto(true, null, disk, alerts, now);
        } catch (Exception e) {
            log.warn("Failed to fetch cluster alerts from Prometheus: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return ClusterAlertsResponseDto.unavailable(
                    e.getClass().getSimpleName() + ": " + e.getMessage(), now);
        }
    }

    /**
     * Join the three per-instance vectors into one row per (instance, mountpoint).
     * Any of the three values may be null when a vector was missing the instance —
     * the FE renders nulls as em-dashes per ADR-0001.
     */
    static List<DiskGaugeDto> joinDisk(List<InstantSample> used,
                                       List<InstantSample> avail,
                                       List<InstantSample> size) {
        Map<String, Map<String, String>> labels = new LinkedHashMap<>();
        Map<String, Double> usedByInstance = new HashMap<>();
        Map<String, Double> availByInstance = new HashMap<>();
        Map<String, Double> sizeByInstance = new HashMap<>();
        indexBy(used, labels, usedByInstance);
        indexBy(avail, labels, availByInstance);
        indexBy(size, labels, sizeByInstance);

        List<DiskGaugeDto> out = new ArrayList<>(labels.size());
        for (Map.Entry<String, Map<String, String>> e : labels.entrySet()) {
            String inst = e.getKey();
            Map<String, String> l = e.getValue();
            Double availV = availByInstance.get(inst);
            Double sizeV = sizeByInstance.get(inst);
            out.add(new DiskGaugeDto(
                    inst,
                    l.getOrDefault("mountpoint", "/"),
                    usedByInstance.get(inst),
                    availV == null ? null : availV.longValue(),
                    sizeV == null ? null : sizeV.longValue()
            ));
        }
        out.sort(Comparator.comparing(DiskGaugeDto::node, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private static void indexBy(List<InstantSample> samples,
                                Map<String, Map<String, String>> labels,
                                Map<String, Double> values) {
        for (InstantSample s : samples) {
            String inst = s.labels().get("instance");
            if (inst == null) continue;
            labels.putIfAbsent(inst, s.labels());
            values.put(inst, s.value());
        }
    }

    static List<ActiveAlertDto> mapAndSortAlerts(List<AlertEnvelope> raw) {
        List<ActiveAlertDto> mapped = new ArrayList<>(raw.size());
        for (AlertEnvelope a : raw) {
            Map<String, String> l = a.labels();
            Map<String, String> ann = a.annotations();
            String instance = l.get("instance");
            if (instance == null) instance = l.get("node");
            mapped.add(new ActiveAlertDto(
                    l.getOrDefault("alertname", "unknown"),
                    l.getOrDefault("severity", "none"),
                    a.state(),
                    instance,
                    ann.get("summary"),
                    ann.get("description"),
                    a.activeAt()
            ));
        }
        mapped.sort(SEVERITY_DESC_THEN_ACTIVE_DESC);
        return mapped;
    }

    /**
     * Severity desc ({@code critical &gt; warning &gt; info &gt; other}), then
     * {@code activeSince} desc (newer alerts first within the same severity tier).
     */
    static final Comparator<ActiveAlertDto> SEVERITY_DESC_THEN_ACTIVE_DESC = (a, b) -> {
        int cmp = Integer.compare(severityRank(b.severity()), severityRank(a.severity()));
        if (cmp != 0) return cmp;
        Instant aAt = a.activeSince();
        Instant bAt = b.activeSince();
        if (aAt == null && bAt == null) return 0;
        if (aAt == null) return 1;
        if (bAt == null) return -1;
        return bAt.compareTo(aAt);
    };

    static int severityRank(String s) {
        if (s == null) return 0;
        return switch (s.toLowerCase()) {
            case "critical" -> 3;
            case "warning" -> 2;
            case "info" -> 1;
            default -> 0;
        };
    }

    /** Test-only hook to drop the cache. */
    void invalidateCacheForTest() {
        cache.set(null);
    }

    /** Local TTL cell — same shape as the one in {@link ServicesStatusService}. */
    private static final class TimedHolder<T> {
        final T value;
        final Instant expiresAt;
        TimedHolder(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
