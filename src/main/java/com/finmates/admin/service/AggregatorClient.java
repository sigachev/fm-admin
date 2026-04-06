package com.finmates.admin.service;

import com.finmates.admin.dto.SourceStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
}
