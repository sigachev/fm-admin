package com.finmates.admin.services.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmates.admin.services.registry.ServiceDescriptor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live k8s-API-backed implementation of {@link PodServiceResolver}.
 *
 * <p>Lists pods in the deployment's own namespace every 30 s (and once eagerly
 * at startup) via raw HTTPS to {@code kubernetes.default.svc}, using the
 * ServiceAccount token + CA bundle that k8s auto-mounts at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/}. No additional
 * dependency added to the pom — uses {@link HttpClient} (JDK) + Jackson
 * (already on classpath).
 *
 * <p><b>Graceful degrade contract (load-bearing):</b>
 * <ul>
 *   <li>{@link PostConstruct} initialisation NEVER throws. If SSLContext or
 *       HttpClient setup fails, the bean comes up with an empty map and the
 *       {@code @Scheduled} loop keeps retrying — the dashboard endpoints serve
 *       null serviceName / null metadata until recovery, exactly like the
 *       Phase-1a NoOp default.</li>
 *   <li>Each refresh tick is wrapped in try/catch. On failure the previous
 *       last-good map is preserved; "stale is better than empty". The first
 *       successful tick atomically replaces the map.</li>
 *   <li>The {@code @Scheduled} method itself never throws (would suppress
 *       future scheduled invocations).</li>
 * </ul>
 *
 * <p>Marked {@link Primary} so it wins the bean injection in
 * {@link com.finmates.admin.service.ServicesStatusService} over the existing
 * {@link NoOpPodServiceResolver}; the NoOp remains available for tests + as a
 * documented fallback.
 */
@Component
@Primary
public class K8sPodServiceResolver implements PodServiceResolver {

    private static final Logger log = LoggerFactory.getLogger(K8sPodServiceResolver.class);

    private static final String K8S_API_HOST = "https://kubernetes.default.svc";
    private static final String SA_DIR = "/var/run/secrets/kubernetes.io/serviceaccount";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final List<ServiceDescriptor> descriptors;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Index keyed by pod IP. Multi-replica services contribute multiple entries. */
    private final AtomicReference<Map<String, PodEntry>> ipIndex = new AtomicReference<>(Map.of());

    private final String saTokenPath;
    private final String caCertPath;
    private final String namespacePath;

    private HttpClient httpClient;   // null if init failed — graceful degrade
    private String authHeader;       // "Bearer <token>" — null if init failed
    private String namespace;        // resolved from the mounted file at init

    public K8sPodServiceResolver(
            List<ServiceDescriptor> descriptors,
            @Value("${k8s.sa.token-path:" + SA_DIR + "/token}") String saTokenPath,
            @Value("${k8s.sa.ca-cert-path:" + SA_DIR + "/ca.crt}") String caCertPath,
            @Value("${k8s.sa.namespace-path:" + SA_DIR + "/namespace}") String namespacePath) {
        this.descriptors = descriptors;
        this.saTokenPath = saTokenPath;
        this.caCertPath = caCertPath;
        this.namespacePath = namespacePath;
    }

    @PostConstruct
    void init() {
        try {
            initHttpClient();
            refresh();
        } catch (RuntimeException e) {
            // Catch RuntimeException only — checked exceptions don't escape Java method bodies
            // we control here. Anything else (Error) we let propagate as it indicates JVM trouble.
            log.warn("K8sPodServiceResolver init failed — serving empty map (will retry on next tick): {}", e.toString());
        }
    }

    private void initHttpClient() {
        try {
            byte[] caBytes = Files.readAllBytes(Path.of(caCertPath));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert;
            try (ByteArrayInputStream in = new ByteArrayInputStream(caBytes)) {
                caCert = (X509Certificate) cf.generateCertificate(in);
            }
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("k8s-ca", caCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();

            String token = Files.readString(Path.of(saTokenPath)).trim();
            this.authHeader = "Bearer " + token;

            this.namespace = Files.readString(Path.of(namespacePath)).trim();
            if (namespace.isEmpty()) {
                throw new IllegalStateException("namespace file is empty");
            }

            log.info("K8sPodServiceResolver initialised: namespace={}, apiHost={}", namespace, K8S_API_HOST);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("k8s client init failed", e);
        }
    }

    /**
     * Refresh tick. Runs every 30 s (fixedDelay, not fixedRate — slow ticks
     * never overlap). Try/catch so a transient API failure does not kill the
     * scheduled loop; the last-good map is preserved.
     */
    @Scheduled(fixedDelay = 30_000L)
    public void scheduledRefresh() {
        if (httpClient == null || authHeader == null || namespace == null) {
            // Init failed on startup — try once more here (e.g. RBAC may have just been applied).
            try {
                initHttpClient();
            } catch (RuntimeException e) {
                log.debug("K8sPodServiceResolver still uninitialised: {}", e.toString());
                return;
            }
        }
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Pod list refresh failed — keeping last-good map ({} entries): {}",
                    ipIndex.get().size(), e.toString());
        }
    }

    /** One k8s API call + parse + index build. */
    private void refresh() {
        String url = K8S_API_HOST + "/api/v1/namespaces/" + namespace + "/pods";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("k8s API call failed", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("k8s API returned HTTP " + response.statusCode());
        }

        Map<String, PodEntry> next = parsePods(response.body());
        ipIndex.set(next);
        log.debug("Pod index refreshed: {} entries", next.size());
    }

    private Map<String, PodEntry> parsePods(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) return Map.of();

            // appLabel -> registry serviceName (e.g. "main" -> "main"; ours are 1:1 today)
            Map<String, String> labelToService = new HashMap<>();
            for (ServiceDescriptor d : descriptors) {
                if (d.appLabel() != null) labelToService.put(d.appLabel(), d.name());
            }

            Map<String, PodEntry> next = new HashMap<>();
            for (JsonNode pod : items) {
                String podIp = pod.path("status").path("podIP").asText(null);
                if (podIp == null || podIp.isBlank()) continue;
                String podName = pod.path("metadata").path("name").asText(null);
                String appLabel = pod.path("metadata").path("labels").path("app").asText(null);
                String serviceName = appLabel != null ? labelToService.get(appLabel) : null;

                // Image: first container's image (deployments + statefulsets used here all run a single container)
                String image = null;
                JsonNode containers = pod.path("spec").path("containers");
                if (containers.isArray() && containers.size() > 0) {
                    image = containers.get(0).path("image").asText(null);
                }

                // startedAt: ISO-8601 timestamp; parsing failure → null (degrade)
                Instant startedAt = null;
                String startTimeStr = pod.path("status").path("startTime").asText(null);
                if (startTimeStr != null && !startTimeStr.isBlank()) {
                    try {
                        startedAt = Instant.parse(startTimeStr);
                    } catch (RuntimeException ignored) {
                        // leave null
                    }
                }
                next.put(podIp, new PodEntry(podName, appLabel, serviceName, image, startedAt));
            }
            return Collections.unmodifiableMap(next);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("pod list parse failed", e);
        }
    }

    // ── PodServiceResolver implementation ────────────────────────────────────

    @Override
    public Optional<String> getServiceNameForPodIp(String podIp) {
        if (podIp == null) return Optional.empty();
        PodEntry e = ipIndex.get().get(podIp);
        return e == null ? Optional.empty() : Optional.ofNullable(e.serviceName());
    }

    @Override
    public Set<String> getPodIpsForService(String serviceName) {
        if (serviceName == null) return Set.of();
        Set<String> ips = new HashSet<>();
        for (Map.Entry<String, PodEntry> e : ipIndex.get().entrySet()) {
            if (serviceName.equals(e.getValue().serviceName())) ips.add(e.getKey());
        }
        return ips;
    }

    @Override
    public Optional<PodMetadata> getMetadataForService(String serviceName) {
        if (serviceName == null) return Optional.empty();
        // First-seen representative (single-replica services have exactly one entry;
        // multi-replica services pick whichever the iteration order surfaces first —
        // image is identical across replicas, startedAt is "a representative pod's start").
        for (PodEntry e : ipIndex.get().values()) {
            if (serviceName.equals(e.serviceName())) {
                return Optional.of(new PodMetadata(e.image(), e.startedAt()));
            }
        }
        return Optional.empty();
    }

    /** Internal index entry. Package-private so a future test can verify map shape directly. */
    record PodEntry(String podName, String appLabel, String serviceName, String image, Instant startedAt) {
    }
}
