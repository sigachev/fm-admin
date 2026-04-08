package com.finmates.admin.service;

import com.finmates.admin.dto.AvailableTokenDto;
import com.finmates.admin.dto.SourceTokensDto;
import com.finmates.admin.entity.aggregator.AdminAsset;
import com.finmates.admin.repository.aggregator.AdminAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for discovering and managing available tokens from aggregator sources.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("aggregatorTransactionManager")
public class TokenDiscoveryService {

    private final AggregatorClient aggregatorClient;
    private final AdminAssetRepository assetRepository;

    /**
     * Get available tokens per source from the aggregator.
     */
    public List<SourceTokensDto> getSourceTokens() {
        return aggregatorClient.getAvailableTokensPerSource();
    }

    /**
     * Get all unique available tokens across all sources.
     * Marks which ones are already in the asset table.
     */
    public List<AvailableTokenDto> getAllAvailableTokens() {
        // Get existing asset symbols
        Set<String> existingSymbols = assetRepository.findAll()
            .stream()
            .map(AdminAsset::getSymbol)
            .collect(Collectors.toSet());

        // Get available tokens from aggregator
        List<SourceTokensDto> sourceTokens = getSourceTokens();

        // Build a map of symbol -> sources
        var tokenMap = new java.util.HashMap<String, java.util.Set<String>>();
        sourceTokens.forEach(source ->
            source.getTokens().forEach(token ->
                tokenMap.computeIfAbsent(token, k -> new java.util.HashSet<>())
                    .add(source.getSourceId())
            )
        );

        // Convert to AvailableTokenDto
        return tokenMap.entrySet().stream()
            .map(entry -> AvailableTokenDto.builder()
                .symbol(entry.getKey())
                .sources(entry.getValue().stream().sorted().collect(Collectors.toList()))
                .alreadyAdded(existingSymbols.contains(entry.getKey()))
                .build())
            .sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol()))
            .collect(Collectors.toList());
    }

    /**
     * Add a new token to the asset table.
     * Optionally provide name and rank, otherwise they're set to defaults.
     */
    public AdminAsset addToken(String symbol, String name, Integer rank) {
        // Check if already exists
        if (assetRepository.findBySymbolIgnoreCase(symbol).isPresent()) {
            throw new IllegalArgumentException("Token " + symbol + " already exists");
        }

        AdminAsset asset = new AdminAsset();
        asset.setSymbol(symbol);
        asset.setName(name != null ? name : symbol);
        asset.setIsActive(true);
        asset.setRank(rank);
        asset.setCreatedAt(Instant.now());

        return assetRepository.save(asset);
    }

    /**
     * Add multiple tokens to the asset table in one call.
     * Skips symbols that already exist. Returns count of newly inserted tokens.
     */
    public int addTokensBulk(List<String> symbols) {
        Set<String> existing = assetRepository.findAll()
            .stream()
            .map(AdminAsset::getSymbol)
            .collect(Collectors.toSet());

        int added = 0;
        for (String symbol : symbols) {
            String upper = symbol.toUpperCase();
            if (existing.contains(upper)) continue;
            AdminAsset asset = new AdminAsset();
            asset.setSymbol(upper);
            asset.setName(upper);
            asset.setIsActive(true);
            asset.setCreatedAt(Instant.now());
            assetRepository.save(asset);
            existing.add(upper);
            added++;
        }
        log.info("Bulk added {} new tokens to asset table", added);
        return added;
    }

    /**
     * Reload assets in the aggregator to pick up newly-added tokens.
     * Should be called after adding tokens via addToken().
     */
    public void reloadAggregatorAssets() {
        try {
            aggregatorClient.reloadAssets();
            log.info("Aggregator assets reloaded successfully");
        } catch (Exception e) {
            log.warn("Failed to reload aggregator assets: {}", e.getMessage());
            // Don't throw — graceful degradation if aggregator is down
        }
    }
}
