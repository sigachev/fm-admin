package com.finmates.admin.service;

import com.finmates.admin.dto.SourceTickerConfigDto;
import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
import com.finmates.admin.repository.aggregator.AggregatorSourceTickerConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSourceTickerService {

    private final AggregatorSourceTickerConfigRepository repo;

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
