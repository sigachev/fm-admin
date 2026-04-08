package com.finmates.admin.service;

import com.finmates.admin.dto.DiscoveryStatusDto;
import com.finmates.admin.dto.SourceAvailableTokenDto;
import com.finmates.admin.dto.SourceTickerConfigDto;
import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
import com.finmates.admin.repository.aggregator.AggregatorSourceTickerConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSourceTickerService {

    private final AggregatorSourceTickerConfigRepository repo;
    private final AggregatorClient aggregatorClient;

    private final AtomicBoolean discovering = new AtomicBoolean(false);
    private volatile Instant discoverStartedAt;
    private volatile Instant discoverCompletedAt;
    private volatile Map<String, Integer> lastDiscoverResults = Collections.emptyMap();

    /**
     * Get all ticker configurations grouped by source
     */
    public Map<String, List<SourceTickerConfigDto>> getAllSourceTickers() {
        return repo.findAll().stream()
            .map(this::toDto)
            .collect(Collectors.groupingBy(SourceTickerConfigDto::sourceId));
    }

    /**
     * Get all tickers for a specific source
     */
    public List<SourceTickerConfigDto> getSourceTickers(String sourceId) {
        return repo.findBySourceId(sourceId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Toggle a ticker's enabled status for a source
     */
    public SourceTickerConfigDto toggleTicker(String sourceId, String symbol, boolean enabled) {
        return repo.findBySourceIdAndSymbol(sourceId, symbol.toUpperCase())
            .map(config -> {
                config.setEnabled(enabled);
                config.setUpdatedAt(Instant.now());
                return repo.save(config);
            })
            .map(this::toDto)
            .orElseThrow(() -> new IllegalArgumentException(
                "Ticker not found: " + sourceId + "/" + symbol));
    }

    /**
     * Trigger async token discovery. Returns immediately; discovery runs in background.
     * Poll getDiscoveryStatus() to track progress.
     * No-op if discovery is already running.
     */
    @Async
    public void discoverTokensAsync() {
        if (!discovering.compareAndSet(false, true)) {
            log.info("Token discovery already in progress, skipping");
            return;
        }
        discoverStartedAt = Instant.now();
        discoverCompletedAt = null;
        lastDiscoverResults = Collections.emptyMap();
        log.info("Starting async token discovery");
        try {
            lastDiscoverResults = aggregatorClient.discoverAndSeedTokens();
            discoverCompletedAt = Instant.now();
            log.info("Async token discovery completed: {}", lastDiscoverResults);
        } catch (Exception e) {
            log.error("Async token discovery failed: {}", e.getMessage());
            discoverCompletedAt = Instant.now();
        } finally {
            discovering.set(false);
        }
    }

    /**
     * Returns the current state of the background discovery job.
     */
    public DiscoveryStatusDto getDiscoveryStatus() {
        return new DiscoveryStatusDto(
            discovering.get(),
            discoverStartedAt,
            discoverCompletedAt,
            lastDiscoverResults
        );
    }

    /**
     * Get available tokens from a specific source for browsing/importing.
     * Reads from source_ticker_config DB (populated by discoverTokens()).
     * isNew=true means the token has not been enabled yet (is_enabled=false).
     */
    public List<SourceAvailableTokenDto> getAvailableTokens(String sourceId, boolean onlyNew) {
        return repo.findBySourceId(sourceId).stream()
            .filter(config -> !onlyNew || !config.isEnabled())
            .map(config -> new SourceAvailableTokenDto(
                config.getSymbol(),
                config.getExchangeSymbol(),
                config.getRestSymbol(),
                !config.isEnabled(),
                config.isEnabled()
            ))
            .collect(Collectors.toList());
    }

    private SourceTickerConfigDto toDto(AggregatorSourceTickerConfig entity) {
        return new SourceTickerConfigDto(
            entity.getId(),
            entity.getSourceId(),
            entity.getSymbol(),
            entity.getExchangeSymbol(),
            entity.getRestSymbol(),
            entity.isEnabled(),
            entity.getUpdatedAt()
        );
    }
}
