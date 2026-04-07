package com.finmates.admin.service;

import com.finmates.admin.dto.AvailableTokenDto;
import com.finmates.admin.dto.SourceAvailableTokenDto;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.SourceTokensDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for calling fm-crypto-aggregator's internal endpoints.
 * Used to fetch source health and other aggregator data.
 */
@Slf4j
@Service
public class AggregatorClient {

    private final String aggregatorUrl;
    private final RestTemplate restTemplate;

    public AggregatorClient(
            @Value("${aggregator.url:http://localhost:8088}") String aggregatorUrl,
            RestTemplate restTemplate) {
        this.aggregatorUrl = aggregatorUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch connection status of all data sources (Hyperliquid, Kraken, Coinbase, OKX, Gemini).
     * Calls the internal endpoint `/internal/v1/health/sources`.
     *
     * @return List of source status; empty list if call fails (graceful degradation)
     */
    public List<SourceStatusDto> getSourceHealth() {
        try {
            String url = aggregatorUrl + "/internal/v1/health/sources";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<SourceStatusDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    SourceStatusDto[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            log.warn("Unexpected response from aggregator health endpoint: status={}", response.getStatusCodeValue());
            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Failed to fetch source health from aggregator: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get available tokens per source from the aggregator.
     * Calls `/internal/v1/discovery/tokens`.
     *
     * @return List of source token availability; empty list if call fails
     */
    public List<SourceTokensDto> getAvailableTokensPerSource() {
        try {
            String url = aggregatorUrl + "/internal/v1/discovery/tokens";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<SourceTokensDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    SourceTokensDto[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            log.warn("Unexpected response from aggregator discovery endpoint: status={}", response.getStatusCodeValue());
            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Failed to fetch available tokens from aggregator: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all unique available tokens across all sources.
     * Calls `/internal/v1/discovery/tokens/all`.
     *
     * @return List of all available tokens with source info; empty list if call fails
     */
    public List<AvailableTokenDto> getAllAvailableTokens() {
        try {
            String url = aggregatorUrl + "/internal/v1/discovery/tokens/all";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<AvailableTokenDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    AvailableTokenDto[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            log.warn("Unexpected response from aggregator tokens endpoint: status={}", response.getStatusCodeValue());
            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Failed to fetch all available tokens from aggregator: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Trigger asset reload in the aggregator.
     * Picks up newly-added tokens from the asset table.
     * Calls `POST /internal/v1/discovery/reload-assets`.
     */
    public void reloadAssets() {
        try {
            String url = aggregatorUrl + "/internal/v1/discovery/reload-assets";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Aggregator assets reloaded successfully");
            } else {
                log.warn("Aggregator reload returned status: {}", response.getStatusCodeValue());
            }

        } catch (Exception e) {
            log.warn("Failed to reload aggregator assets: {}", e.getMessage());
            // Don't throw — graceful degradation if aggregator is down
        }
    }

    /**
     * Discover and seed all available tokens from exchange REST APIs.
     * Calls `POST /internal/v1/discovery/seed`.
     * Returns a map of sourceId -> count of newly inserted rows.
     * Note: This operation can be long-running (30+ seconds) depending on exchange API response times.
     */
    public java.util.Map<String, Integer> discoverAndSeedTokens() {
        try {
            String url = aggregatorUrl + "/internal/v1/discovery/seed";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    java.util.Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Token discovery and seeding completed: {}", response.getBody());
                return response.getBody();
            }
            log.warn("Token discovery returned status: {}", response.getStatusCodeValue());
            return Collections.emptyMap();

        } catch (ResourceAccessException e) {
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Aggregator service unavailable, timeout, or slow response during token discovery: {}", errorMsg);
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Token discovery and seeding failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Get available tokens from a specific source for browsing/importing.
     * Calls `GET /internal/v1/discovery/available?sourceId={sourceId}&onlyNew={onlyNew}`.
     *
     * @param sourceId source ID (kraken, coinbase, okx, gemini, hyperliquid)
     * @param onlyNew if true, only return tokens not yet in our config
     * @return List of available tokens from the source; empty list if call fails
     */
    public List<SourceAvailableTokenDto> getAvailableTokens(String sourceId, boolean onlyNew) {
        try {
            String url = aggregatorUrl + "/internal/v1/discovery/available?sourceId=" + sourceId + "&onlyNew=" + onlyNew;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "fm-admin");

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<SourceAvailableTokenDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    SourceAvailableTokenDto[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            log.warn("Get available tokens returned status: {}", response.getStatusCodeValue());
            return Collections.emptyList();

        } catch (ResourceAccessException e) {
            // Likely a timeout or connection error
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Aggregator service unavailable or timeout for source {}: {}", sourceId, errorMsg);
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch available tokens from source {}: {}", sourceId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
