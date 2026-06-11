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
 * <p>Per-service health probes are run sequentially with the short-timeout
 * {@code healthRestTemplate} (1 s connect / 2 s read). With 9 probed services
 * that bounds worst-case latency to ~18 s for fully-unreachable hosts; in
 * practice the cache pays for that. If real-world latency proves painful we can
 * parallelise via {@code CompletableFuture.supplyAsync}, but starting simple.
 */
@Service
public class ServicesStatusService {

    private static final Logger log = LoggerFactory.getLogger(ServicesStatusService.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    /** {@code 10.244.0.0/16} — the dev cluster's pod CIDR. */
    private static final int POD_CIDR_PREFIX = 0x0AF40000; // 10.244.0.0
    private static final int POD_CIDR_MASK   = 0xFFFF0000; // /16

    private final List<ServiceDescriptor> descriptors;
    private final ServicesHealthClient healthClient;
    private final PgConnectionService pgConnectionService;
    private final PodServiceResolver podResolver;

    private final AtomicReference<TimedHolder<List<ServiceStatusDto>>> statusCache = new AtomicReference<>();
    private final AtomicReference<TimedHolder<DbConnectionsResponseDto>> connectionsCache = new AtomicReference<>();

    public ServicesStatusService(List<ServiceDescriptor> descriptors,
                                 ServicesHealthClient healthClient,
                                 PgConnectionService pgConnectionService,
                                 PodServiceResolver podResolver) {
        this.descriptors = descriptors;
        this.healthClient = healthClient;
        this.pgConnectionService = pgConnectionService;
        this.podResolver = podResolver;
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
        List<ServiceStatusDto> out = new ArrayList<>(descriptors.size());
        for (ServiceDescriptor d : descriptors) {
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
            out.add(new ServiceStatusDto(
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
            ));
        }
        return out;
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

    /** Test-only hook to drop both caches (kept package-private intentionally). */
    void invalidateCachesForTest() {
        statusCache.set(null);
        connectionsCache.set(null);
        log.debug("Services dashboard caches invalidated");
    }
}
