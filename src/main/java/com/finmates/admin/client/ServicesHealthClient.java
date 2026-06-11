package com.finmates.admin.client;

import com.finmates.admin.dto.services.HealthStatus;
import com.finmates.admin.services.registry.HealthType;
import com.finmates.admin.services.registry.ServiceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Per-service health probe used by {@code GET /api/admin/services/status}.
 *
 * <p>Hard requirement: this client MUST be wired with the short-timeout
 * {@code healthRestTemplate} bean (1 s connect / 2 s read). Using the default
 * RestTemplate would let one slow service stall the whole dashboard request.
 *
 * <p>Honest degradation per {@link HealthStatus}: 401 / timeout / connection
 * refused / unparseable body &rarr; UNKNOWN (we cannot tell). A real 503 from a
 * service that genuinely reports {@code DOWN} is surfaced verbatim — that is a
 * meaningful signal (e.g. fm-social today).
 */
@Component
public class ServicesHealthClient {

    private static final Logger log = LoggerFactory.getLogger(ServicesHealthClient.class);

    private final RestTemplate healthRestTemplate;

    public ServicesHealthClient(@Qualifier("healthRestTemplate") RestTemplate healthRestTemplate) {
        this.healthRestTemplate = healthRestTemplate;
    }

    /**
     * @return a {@link HealthProbeResult} — never null. {@code error} is non-null
     *         only when the probe failed; on UP/DOWN the status field carries
     *         the real signal.
     */
    public HealthProbeResult probe(ServiceDescriptor descriptor) {
        try {
            return switch (descriptor.healthType()) {
                case ACTUATOR, KEYCLOAK_Q -> probeJsonHealth(descriptor);
                case REDIS_PING -> probeRedisTcp(descriptor);
                case NONE -> new HealthProbeResult(HealthStatus.UNKNOWN, "no probe configured");
            };
        } catch (RuntimeException e) {
            // Defensive: anything we did not catch deeper is UNKNOWN, not DOWN.
            log.debug("Unexpected error probing {}: {}", descriptor.name(), e.toString());
            return new HealthProbeResult(HealthStatus.UNKNOWN, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private HealthProbeResult probeJsonHealth(ServiceDescriptor descriptor) {
        String url = descriptor.healthUrl();
        if (url == null) {
            return new HealthProbeResult(HealthStatus.UNKNOWN, "healthUrl not configured");
        }
        try {
            ResponseEntity<Map> response = healthRestTemplate.getForEntity(url, Map.class);
            Object statusVal = response.getBody() != null ? response.getBody().get("status") : null;
            if (statusVal == null) {
                return new HealthProbeResult(HealthStatus.UNKNOWN, "no 'status' field in response");
            }
            String s = statusVal.toString().toUpperCase();
            return switch (s) {
                case "UP" -> new HealthProbeResult(HealthStatus.UP, null);
                case "DOWN", "OUT_OF_SERVICE" -> new HealthProbeResult(HealthStatus.DOWN, null);
                default -> new HealthProbeResult(HealthStatus.UNKNOWN, "unrecognized status: " + s);
            };
        } catch (HttpServerErrorException e) {
            // 503 with a parseable body is the canonical "service reports DOWN" — try to read it.
            String body = e.getResponseBodyAsString();
            if (body != null && body.toUpperCase().contains("\"STATUS\":\"DOWN\"")) {
                return new HealthProbeResult(HealthStatus.DOWN, null);
            }
            return new HealthProbeResult(HealthStatus.UNKNOWN, "HTTP " + e.getStatusCode());
        } catch (HttpClientErrorException e) {
            // 401 / 403 / 404 — endpoint is secured or wrong path; we cannot tell.
            return new HealthProbeResult(HealthStatus.UNKNOWN, "HTTP " + e.getStatusCode());
        } catch (Exception e) {
            return new HealthProbeResult(HealthStatus.UNKNOWN, e.getClass().getSimpleName());
        }
    }

    /**
     * Lightweight redis liveness: a 1.5 s TCP connect to {@code redis:6379}.
     * We deliberately do NOT speak the redis RESP protocol — opening a port-6379
     * socket is enough to say "redis is accepting connections" without dragging in
     * a Lettuce/Jedis client just for this.
     */
    private HealthProbeResult probeRedisTcp(ServiceDescriptor descriptor) {
        String host = descriptor.appLabel(); // service-DNS name
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, 6379), 1500);
            return new HealthProbeResult(HealthStatus.UP, null);
        } catch (Exception e) {
            return new HealthProbeResult(HealthStatus.UNKNOWN, "tcp connect failed: " + e.getClass().getSimpleName());
        }
    }

    public record HealthProbeResult(HealthStatus status, String error) {
    }
}
