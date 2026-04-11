package com.finmates.admin.service;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.CreateTokenRequest;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.UpdateTokenRequest;
import com.finmates.admin.entity.aggregator.AdminAsset;
import com.finmates.admin.repository.aggregator.AdminAssetRepository;
import com.finmates.admin.repository.aggregator.AggregatorSourceTickerConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing crypto tokens/assets in the aggregator DB.
 * Provides CRUD operations and source health information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional("aggregatorTransactionManager")
public class AdminTokenService {

    private final AdminAssetRepository assetRepository;
    private final AggregatorSourceTickerConfigRepository sourceTickerRepo;
    private final AggregatorClient aggregatorClient;

    /**
     * List all tokens with optional search and source filter.
     * When sourceId is provided, only returns tokens that have an enabled ticker for that source.
     */
    public Page<AdminTokenDto> listTokens(String search, String sourceId, Pageable pageable) {
        Page<AdminAsset> assets;

        if (sourceId != null && !sourceId.isBlank()) {
            // Collect enabled symbols for this source (normalised to uppercase to match asset table)
            Set<String> symbols = sourceTickerRepo.findEnabledBySourceId(sourceId)
                    .stream()
                    .map(t -> t.getSymbol().toUpperCase())
                    .collect(Collectors.toSet());

            if (symbols.isEmpty()) {
                return Page.empty(pageable);
            }

            if (search != null && !search.isBlank()) {
                assets = assetRepository.findBySymbolInAndSearchTerm(symbols, search.trim(), pageable);
            } else {
                assets = assetRepository.findBySymbolIn(symbols, pageable);
            }
        } else if (search != null && !search.isBlank()) {
            String s = search.trim();
            assets = assetRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(s, s, pageable);
        } else {
            assets = assetRepository.findAll(pageable);
        }

        return assets.map(this::toDto);
    }

    /**
     * Get a token by symbol.
     */
    public AdminTokenDto getToken(String symbol) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));
        return toDto(asset);
    }

    /**
     * Create a new token.
     */
    public AdminTokenDto createToken(CreateTokenRequest request) {
        // Validate input
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }

        // Check if symbol already exists
        if (assetRepository.findBySymbolIgnoreCase(request.getSymbol()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Symbol already exists");
        }

        // Create and save
        AdminAsset asset = new AdminAsset();
        asset.setSymbol(request.getSymbol().toUpperCase());
        asset.setName(request.getName());
        asset.setRank(request.getRank());
        asset.setIsActive(true);
        asset.setCreatedAt(Instant.now());

        AdminAsset saved = assetRepository.save(asset);
        log.info("Created token: symbol={}, name={}", saved.getSymbol(), saved.getName());

        // Reload aggregator asset list so the new token starts streaming immediately
        try {
            aggregatorClient.reloadAssets();
        } catch (Exception e) {
            log.warn("Created token {} but failed to reload aggregator assets: {}", saved.getSymbol(), e.getMessage());
        }

        return toDto(saved);
    }

    /**
     * Update a token's metadata (name, rank).
     */
    public AdminTokenDto updateToken(String symbol, UpdateTokenRequest request) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));

        if (request.getName() != null && !request.getName().isBlank()) {
            asset.setName(request.getName());
        }
        if (request.getRank() != null) {
            asset.setRank(request.getRank());
        }
        if (request.getLogoUrl() != null) {
            asset.setLogoUrl(request.getLogoUrl().isBlank() ? null : request.getLogoUrl());
        }
        if (request.getDescription() != null) {
            asset.setDescription(request.getDescription().isBlank() ? null : request.getDescription());
        }
        if (request.getWebsite() != null) {
            asset.setWebsite(request.getWebsite().isBlank() ? null : request.getWebsite());
        }
        if (request.getTwitter() != null) {
            asset.setTwitter(request.getTwitter().isBlank() ? null : request.getTwitter());
        }
        if (request.getWhitepaper() != null) {
            asset.setWhitepaper(request.getWhitepaper().isBlank() ? null : request.getWhitepaper());
        }

        AdminAsset updated = assetRepository.save(asset);
        log.info("Updated token: symbol={}", symbol);

        return toDto(updated);
    }

    /**
     * Toggle a token's active status.
     */
    public AdminTokenDto toggleActive(String symbol) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));

        boolean newActive = !Boolean.TRUE.equals(asset.getIsActive());
        asset.setIsActive(newActive);
        AdminAsset updated = assetRepository.save(asset);

        log.info("Toggled token status: symbol={}, isActive={}", symbol, updated.getIsActive());

        // Reload aggregator so status change takes effect immediately
        try {
            aggregatorClient.reloadAssets();
        } catch (Exception e) {
            log.warn("Toggled token {} but failed to reload aggregator assets: {}", symbol, e.getMessage());
        }

        return toDto(updated);
    }

    /**
     * Delete a token.
     */
    public void deleteToken(String symbol) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));

        assetRepository.delete(asset);
        log.info("Deleted token: symbol={}", symbol);
    }

    /**
     * Get exchange source health status from aggregator.
     */
    public List<SourceStatusDto> getSourceHealth() {
        return aggregatorClient.getSourceHealth();
    }

    /**
     * Trigger OKX metadata fetch via the aggregator service.
     * Returns { updated: N, notFound: M }.
     */
    public java.util.Map<String, Integer> fetchMetadataFromOkx() {
        return aggregatorClient.fetchMetadataFromOkx();
    }

    /**
     * Get total token count.
     */
    public long getTotalTokenCount() {
        return assetRepository.count();
    }

    /**
     * Get active token count.
     */
    public long getActiveTokenCount() {
        return assetRepository.countByIsActiveTrue();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────────

    private AdminTokenDto toDto(AdminAsset asset) {
        return AdminTokenDto.builder()
                .id(asset.getId())
                .symbol(asset.getSymbol())
                .name(asset.getName())
                .isActive(Boolean.TRUE.equals(asset.getIsActive()))
                .rank(asset.getRank())
                .logoUrl(asset.getLogoUrl())
                .description(asset.getDescription())
                .website(asset.getWebsite())
                .twitter(asset.getTwitter())
                .whitepaper(asset.getWhitepaper())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
