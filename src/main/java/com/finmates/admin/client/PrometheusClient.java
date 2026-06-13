package com.finmates.admin.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client over the Prometheus HTTP API ({@code /api/v1/query} and
 * {@code /api/v1/alerts}).
 *
 * <p>Uses the shared {@code healthRestTemplate} bean (2&nbsp;s connect / 5&nbsp;s read)
 * so a Prometheus stall can't hang the admin Services page past the FE's 10&nbsp;s
 * refresh cadence.
 *
 * <p>Failure semantics: every public method either returns a populated result
 * or throws a {@link PrometheusClientException}. The service layer interprets
 * exceptions as "Prometheus unavailable" (ADR-0001 degraded envelope) rather
 * than treating them as fatal — callers must NOT confuse this with the "zero
 * alerts returned" case, which is the happy/quiet path.
 */
@Component
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final String prometheusUrl;
    private final RestTemplate healthRestTemplate;

    public PrometheusClient(
            @Value("${prometheus.url:}") String prometheusUrl,
            @Qualifier("healthRestTemplate") RestTemplate healthRestTemplate) {
        this.prometheusUrl = prometheusUrl == null ? "" : prometheusUrl.trim();
        this.healthRestTemplate = healthRestTemplate;
        if (this.prometheusUrl.isEmpty()) {
            log.info("PrometheusClient: prometheus.url is blank — cluster-alerts will surface available=false");
        } else {
            log.info("PrometheusClient initialized: baseUrl={}", this.prometheusUrl);
        }
    }

    public boolean isConfigured() {
        return !prometheusUrl.isEmpty();
    }

    /**
     * Issue an instant query (resultType=vector) and flatten to {@link InstantSample}s.
     * @throws PrometheusClientException on any HTTP / parse / Prometheus-side error.
     */
    public List<InstantSample> queryInstant(String promql) {
        if (!isConfigured()) {
            throw new PrometheusClientException("prometheus.url is not configured");
        }
        String url = UriComponentsBuilder.fromHttpUrl(prometheusUrl + "/api/v1/query")
                .queryParam("query", promql)
                .build()
                .toUriString();
        QueryResponse body = exchange(url, QueryResponse.class, "queryInstant");
        if (body == null || body.data == null || body.data.result == null) {
            throw new PrometheusClientException("queryInstant: empty data envelope");
        }
        if (!"success".equals(body.status)) {
            throw new PrometheusClientException("queryInstant: status=" + body.status);
        }
        List<InstantSample> out = new ArrayList<>(body.data.result.size());
        for (VectorResult r : body.data.result) {
            Map<String, String> labels = r.metric == null ? Map.of() : r.metric;
            Double value = parseVectorValue(r.value);
            if (value == null) continue;
            out.add(new InstantSample(labels, value));
        }
        return out;
    }

    /**
     * Fetch firing + pending alerts from {@code /api/v1/alerts}.
     * @throws PrometheusClientException on any HTTP / parse / Prometheus-side error.
     */
    public List<AlertEnvelope> getAlerts() {
        if (!isConfigured()) {
            throw new PrometheusClientException("prometheus.url is not configured");
        }
        String url = prometheusUrl + "/api/v1/alerts";
        AlertsResponse body = exchange(url, AlertsResponse.class, "getAlerts");
        if (body == null || body.data == null) {
            throw new PrometheusClientException("getAlerts: empty data envelope");
        }
        if (!"success".equals(body.status)) {
            throw new PrometheusClientException("getAlerts: status=" + body.status);
        }
        List<RawAlert> raw = body.data.alerts == null ? Collections.emptyList() : body.data.alerts;
        List<AlertEnvelope> out = new ArrayList<>(raw.size());
        for (RawAlert a : raw) {
            out.add(new AlertEnvelope(
                    a.labels == null ? Map.of() : a.labels,
                    a.annotations == null ? Map.of() : a.annotations,
                    a.state,
                    a.activeAt
            ));
        }
        return out;
    }

    /**
     * Probe Prometheus reachability cheaply (no series payload) for the rule-existence
     * audit check. Returns true iff {@code /api/v1/rules} contains an alert rule named
     * {@code ruleName}.
     */
    public boolean ruleExists(String ruleName) {
        if (!isConfigured()) return false;
        try {
            RulesResponse body = exchange(prometheusUrl + "/api/v1/rules", RulesResponse.class, "ruleExists");
            if (body == null || body.data == null || body.data.groups == null) return false;
            for (RuleGroup g : body.data.groups) {
                if (g.rules == null) continue;
                for (Rule r : g.rules) {
                    if (ruleName.equals(r.name)) return true;
                }
            }
        } catch (Exception e) {
            log.debug("ruleExists({}) lookup failed: {}", ruleName, e.toString());
        }
        return false;
    }

    private <T> T exchange(String url, Class<T> clazz, String op) {
        try {
            ResponseEntity<T> resp = healthRestTemplate.getForEntity(url, clazz);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new PrometheusClientException(op + ": HTTP " + resp.getStatusCode().value());
            }
            return resp.getBody();
        } catch (PrometheusClientException e) {
            throw e;
        } catch (Exception e) {
            throw new PrometheusClientException(op + ": " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Prometheus instant-query vectors return {@code value: [timestamp, "stringNumber"]}.
     * Parse the second element to a double; return null on malformed input.
     */
    private static Double parseVectorValue(Object[] value) {
        if (value == null || value.length < 2 || value[1] == null) return null;
        try {
            return Double.parseDouble(value[1].toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Public records ───────────────────────────────────────────────────────

    public record InstantSample(Map<String, String> labels, double value) {
    }

    public record AlertEnvelope(
            Map<String, String> labels,
            Map<String, String> annotations,
            String state,
            Instant activeAt) {
    }

    // ── Jackson wire shapes (package-private; mutable so Jackson can populate) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class QueryResponse {
        public String status;
        public QueryData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class QueryData {
        public String resultType;
        public List<VectorResult> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class VectorResult {
        public LinkedHashMap<String, String> metric;
        public Object[] value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AlertsResponse {
        public String status;
        public AlertsData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AlertsData {
        public List<RawAlert> alerts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RawAlert {
        public LinkedHashMap<String, String> labels;
        public LinkedHashMap<String, String> annotations;
        public String state;
        public Instant activeAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RulesResponse {
        public String status;
        public RulesData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RulesData {
        public List<RuleGroup> groups;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RuleGroup {
        public String name;
        public List<Rule> rules;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Rule {
        public String name;
        public String type;
    }

    public static class PrometheusClientException extends RuntimeException {
        public PrometheusClientException(String message) {
            super(message);
        }
        public PrometheusClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
