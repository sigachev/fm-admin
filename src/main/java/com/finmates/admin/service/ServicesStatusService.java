package com.finmates.admin.service;

import com.finmates.admin.client.ServicesHealthClient;
import com.finmates.admin.client.ServicesHealthClient.HealthProbeResult;
import com.finmates.admin.dto.services.DbConnectionCountsDto;
import com.finmates.admin.dto.services.DbConnectionRowDto;
import com.finmates.admin.dto.services.DbConnectionsResponseDto;
import com.finmates.admin.dto.services.DbConnectionsSummaryDto;
import com.finmates.admin.dto.services.HealthStatus;
import com.finmates.admin.dto.services.ServiceStatusDto;
import com.finmates.admin.service.PgConnectionService.PgActivityRow;
import com.finmates.admin.services.registry.ServiceDescriptor;
import com.finmates.admin.services.resolver.PodServiceResolver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregates {@link ServiceStatusDto} and the cluster-wide DB-connections
 * response.
 *
 * <p>Cache: a hand-rolled {@link AtomicReference} TTL holder per endpoint.
 * Frontend polls ~10 s; cache TTL ~10 s. Spec called for Caffeine; for two
 * cached values, adding {@code spring-boot-starter-cache} + caffeine just for
 * this is heavier than the inline alternative. If a third caching consumer
 * lands, lift the pattern.
 *
 * <p>Per-service health probes are run concurrently on a bounded pool with the
 * short-timeout {@code healthRestTemplate} (2 s connect / 5 s read). Worst-case
 * wall-clock time is bounded by the slowest single probe (~5 s), not the serial
 * sum — important when one or more services are unreachable and would otherwise
 * stall the dashboard request. Pool is sized at
 * {@code min(descriptors.size(), 12)}, daemon threads so JVM shutdown isn't
 * blocked, and shut down cleanly via {@link PreDestroy}.
 */
@Service
public class ServicesStatusService {

    private static final Logger log = LoggerFactory.getLogger(ServicesStatusService.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    /** Upper bound on the health-probe thread pool — guards against future registry growth. */
    private static final int MAX_PROBE_THREADS = 12;

    /** How long {@link #buildServicesStatus()} will wait for all parallel probes before bailing. */
    private static final Duration PROBE_FANOUT_TIMEOUT = Duration.ofSeconds(15);

    /** {@code 10.244.0.0/16} — the dev cluster's pod CIDR. */
    private static final int POD_CIDR_PREFIX = 0x0AF40000; // 10.244.0.0
    private static final int POD_CIDR_MASK   = 0xFFFF0000; // /16

    private final List<ServiceDescriptor> descriptors;
    private final ServicesHealthClient healthClient;
    private final PgConnectionService pgConnectionService;
    private final PodServiceResolver podResolver;

    private final AtomicReference<TimedHolder<List<ServiceStatusDto>>> statusCache = new AtomicReference<>();
    private final AtomicReference<TimedHolder<DbConnectionsResponseDto>> connectionsCache = new AtomicReference<>();

    /**
     * Bounded pool for parallel health probes. Sized to the registry size (capped at
     * {@link #MAX_PROBE_THREADS}) so each descriptor effectively runs on its own thread —
     * the registry is small (9 today) so there is no queueing in practice. Daemon
     * threads keep JVM shutdown from blocking on an in-flight probe.
     */
    private final ExecutorService probeExecutor;

    public ServicesStatusService(List<ServiceDescriptor> descriptors,
                                 ServicesHealthClient healthClient,
                                 PgConnectionService pgConnectionService,
                                 PodServiceResolver podResolver) {
        this.descriptors = descriptors;
        this.healthClient = healthClient;
        this.pgConnectionService = pgConnectionService;
        this.podResolver = podResolver;
        int poolSize = Math.max(1, Math.min(descriptors.size(), MAX_PROBE_THREADS));
        this.probeExecutor = Executors.newFixedThreadPool(poolSize, daemonThreadFactory("svc-probe-"));
        log.debug("ServicesStatusService health-probe pool initialized: size={}, descriptors={}",
                poolSize, descriptors.size());
    }

    @PreDestroy
    void shutdownProbeExecutor() {
        probeExecutor.shutdown();
        try {
            if (!probeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                probeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            probeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── /api/admin/services/status ───────────────────────────────────────────

    public List<ServiceStatusDto> getServicesStatus() {
        TimedHolder<List<ServiceStatusDto>> cached = statusCache.get();
        if (cached != null && cached.isFresh()) {
            return cached.value;
        }
        List<ServiceStatusDto> built = buildServicesStatus();
        statusCache.set(new TimedHolder<>(built, Instant.now().plus(CACHE_TTL)));
        return built;
    }

    private List<ServiceStatusDto> buildServicesStatus() {
        List<PgActivityRow> activity = pgConnectionService.fetchActivity();
        // Group counts by (datname, podIp, state)
        Map<String, Map<String, int[]>> byDbThenIp = new HashMap<>(); // dbName -> ip -> [active, idle, idleInTx]
        for (PgActivityRow row : activity) {
            if (row.datname() == null || row.clientAddr() == null) continue;
            byDbThenIp
                    .computeIfAbsent(row.datname(), k -> new HashMap<>())
                    .computeIfAbsent(row.clientAddr(), k -> new int[3]);
            int[] slot = byDbThenIp.get(row.datname()).get(row.clientAddr());
            String state = row.state() == null ? "" : row.state();
            switch (state) {
                case "active" -> slot[0] += row.conns();
                case "idle" -> slot[1] += row.conns();
                case "idle in transaction" -> slot[2] += row.conns();
                default -> { /* ignore disabled, fastpath function call, etc. */ }
            }
        }

        Instant now = Instant.now();
        int n = descriptors.size();

        // Fan out per-descriptor probes onto the bounded pool. Results are slotted
        // BY INDEX so the registry order is preserved for the FE table — order matters
        // there (dashboard groups services in a specific visual order).
        ServiceStatusDto[] slots = new ServiceStatusDto[n];
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final ServiceDescriptor d = descriptors.get(i);
            futures[i] = CompletableFuture.runAsync(
                    () -> slots[idx] = buildSingleServiceStatus(d, byDbThenIp, now),
                    probeExecutor);
        }
        try {
            CompletableFuture.allOf(futures)
                    .get(PROBE_FANOUT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Fan-out budget exhausted (a probe blocked past PROBE_FANOUT_TIMEOUT) or
            // interrupted. Don't fail the whole dashboard request — fill any unfilled
            // slots with UNKNOWN("probe timeout") so the FE renders honest degradation
            // for the slow service while the rest of the row populates normally.
            log.warn("Health-probe fan-out did not complete within {}: {}",
                    PROBE_FANOUT_TIMEOUT, e.toString());
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }

        List<ServiceStatusDto> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (slots[i] != null) {
                out.add(slots[i]);
            } else {
                ServiceDescriptor d = descriptors.get(i);
                out.add(new ServiceStatusDto(
                        d.name(), d.displayName(), HealthStatus.UNKNOWN,
                        null, null, null, null, "NONE", now,
                        "probe did not complete within " + PROBE_FANOUT_TIMEOUT));
            }
        }
        return out;
    }

    /**
     * Per-descriptor work: health probe + DB-connection counts + DTO construction.
     * Pulled out of the for-loop body so it can run inside a {@link CompletableFuture}
     * task on the probe pool. Pure function of (descriptor, byDbThenIp, now) + the
     * injected {@code healthClient} / {@code podResolver}.
     */
    private ServiceStatusDto buildSingleServiceStatus(ServiceDescriptor d,
                                                      Map<String, Map<String, int[]>> byDbThenIp,
                                                      Instant now) {
        HealthProbeResult probe = healthClient.probe(d);
        java.util.Optional<com.finmates.admin.services.resolver.PodMetadata> meta =
                podResolver.getMetadataForService(d.name());
        DbConnectionCountsDto counts = null;
        String source = "NONE";
        if (d.dbName() != null) {
            Set<String> podIps = podResolver.getPodIpsForService(d.name());
            if (!podIps.isEmpty()) {
                Map<String, int[]> byIp = byDbThenIp.getOrDefault(d.dbName(), Map.of());
                int active = 0, idle = 0, idleInTx = 0;
                for (String ip : podIps) {
                    int[] slot = byIp.get(ip);
                    if (slot != null) {
                        active += slot[0];
                        idle += slot[1];
                        idleInTx += slot[2];
                    }
                }
                counts = new DbConnectionCountsDto(active, idle, idleInTx);
                source = "PG_STAT_ACTIVITY";
            }
            // If resolver returned no IPs (Phase 1a no-op default), leave counts null + source=NONE.
        }
        return new ServiceStatusDto(
                d.name(),
                d.displayName(),
                probe.status(),
                null,            // version — not collected (no metrics endpoint open; not in 1b scope)
                meta.map(m -> m.startedAt() == null ? null
                        : java.time.Duration.between(m.startedAt(), now).toSeconds()).orElse(null),
                meta.map(com.finmates.admin.services.resolver.PodMetadata::image).orElse(null),
                counts,
                source,
                now,
                probe.error()
        );
    }

    // ── /api/admin/services/db-connections ───────────────────────────────────

    public DbConnectionsResponseDto getDbConnections() {
        TimedHolder<DbConnectionsResponseDto> cached = connectionsCache.get();
        if (cached != null && cached.isFresh()) {
            return cached.value;
        }
        DbConnectionsResponseDto built = buildDbConnections();
        connectionsCache.set(new TimedHolder<>(built, Instant.now().plus(CACHE_TTL)));
        return built;
    }

    private DbConnectionsResponseDto buildDbConnections() {
        List<PgActivityRow> activity = pgConnectionService.fetchActivity();
        List<DbConnectionRowDto> rows = new ArrayList<>(activity.size());
        int total = 0, podTotal = 0, externalTotal = 0;
        for (PgActivityRow row : activity) {
            boolean isPod = isInPodCidr(row.clientAddr());
            boolean isExternal = row.clientAddr() != null && !isPod;
            String serviceName = isPod
                    ? podResolver.getServiceNameForPodIp(row.clientAddr()).orElse(null)
                    : null;
            rows.add(new DbConnectionRowDto(
                    row.datname(),
                    row.clientAddr(),
                    row.applicationName(),
                    row.state(),
                    row.conns(),
                    isPod,
                    isExternal,
                    serviceName
            ));
            total += row.conns();
            if (isPod) podTotal += row.conns();
            if (isExternal) externalTotal += row.conns();
        }
        return new DbConnectionsResponseDto(
                new DbConnectionsSummaryDto(total, podTotal, externalTotal),
                rows,
                Instant.now()
        );
    }

    /** True iff {@code clientAddr} parses to an IPv4 inside {@code 10.244.0.0/16}. */
    private static boolean isInPodCidr(String clientAddr) {
        if (clientAddr == null) return false;
        String[] parts = clientAddr.split("\\.");
        if (parts.length != 4) return false;
        try {
            int ip = (Integer.parseInt(parts[0]) << 24)
                    | (Integer.parseInt(parts[1]) << 16)
                    | (Integer.parseInt(parts[2]) << 8)
                    | Integer.parseInt(parts[3]);
            return (ip & POD_CIDR_MASK) == POD_CIDR_PREFIX;
        } catch (NumberFormatException e) {
            return false; // IPv6 or junk — treat as non-pod (the rare IPv6 hit shows up as 'external' which is fine)
        }
    }

    /** Hand-rolled TTL cell. */
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

    /** Builds daemon threads with a stable name prefix so they're identifiable in thread dumps. */
    private static ThreadFactory daemonThreadFactory(String namePrefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, namePrefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Test-only hook to drop both caches (kept package-private intentionally). */
    void invalidateCachesForTest() {
        statusCache.set(null);
        connectionsCache.set(null);
        log.debug("Services dashboard caches invalidated");
    }
}
