package com.finmates.admin.service;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.CreateTokenRequest;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.UpdateTokenRequest;
import com.finmates.admin.entity.aggregator.AdminAsset;
import com.finmates.admin.repository.aggregator.AdminAssetRepository;
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
    private final AggregatorClient aggregatorClient;

    /**
     * List all tokens with optional search.
     */
    public Page<AdminTokenDto> listTokens(String search, Pageable pageable) {
        Page<AdminAsset> assets;

        if (search != null && !search.isBlank()) {
            search = search.trim();
            assets = assetRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(search, search, pageable);
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
                .createdAt(asset.getCreatedAt())
                .build();
    }
}
