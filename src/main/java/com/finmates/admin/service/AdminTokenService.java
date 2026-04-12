package com.finmates.admin.service;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.CreateTokenRequest;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.UpdateTokenRequest;
import com.finmates.admin.entity.aggregator.AdminAsset;
import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Find case-insensitive duplicate symbols in the asset table.
     * Returns a map of canonical (uppercase) symbol → list of duplicate rows.
     */
    public Map<String, List<AdminTokenDto>> findDuplicates() {
        List<AdminAsset> dupes = assetRepository.findDuplicatesBySymbol();
        Map<String, List<AdminTokenDto>> result = new LinkedHashMap<>();
        for (AdminAsset a : dupes) {
            result.computeIfAbsent(a.getSymbol().toUpperCase(), k -> new ArrayList<>()).add(toDto(a));
        }
        return result;
    }

    /**
     * Safely remove case-insensitive duplicate assets.
     * Strategy: within each duplicate group, keep the row with the lowest ID (oldest/original).
     * Before deleting duplicates, update any source_ticker_config rows that reference the
     * deleted symbol to use the kept symbol's casing.
     * Returns the number of rows deleted.
     */
    public int removeDuplicates() {
        List<AdminAsset> dupes = assetRepository.findDuplicatesBySymbol();
        if (dupes.isEmpty()) return 0;

        // Group by uppercase symbol; rows are already ordered by UPPER(symbol), id (lowest first)
        Map<String, List<AdminAsset>> grouped = new LinkedHashMap<>();
        for (AdminAsset a : dupes) {
            grouped.computeIfAbsent(a.getSymbol().toUpperCase(), k -> new ArrayList<>()).add(a);
        }

        int deleted = 0;
        for (Map.Entry<String, List<AdminAsset>> entry : grouped.entrySet()) {
            List<AdminAsset> group = entry.getValue();
            AdminAsset keep = group.get(0); // lowest id = keep
            for (int i = 1; i < group.size(); i++) {
                AdminAsset dupe = group.get(i);
                // Remap source_ticker_config rows that reference the duplicate symbol
                sourceTickerRepo.findBySourceIdAndSymbolIgnoreCase(keep.getSymbol(), dupe.getSymbol())
                        .ifPresent(cfg -> {
                            cfg.setSymbol(keep.getSymbol());
                            sourceTickerRepo.save(cfg);
                        });
                assetRepository.delete(dupe);
                log.info("Deleted duplicate asset: id={}, symbol='{}' (kept id={}, symbol='{}')",
                        dupe.getId(), dupe.getSymbol(), keep.getId(), keep.getSymbol());
                deleted++;
            }
        }
        return deleted;
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

    /**
     * Bulk set is_active for a list of symbols.
     * Returns the number of rows updated.
     */
    public int bulkSetActive(List<String> symbols, boolean active) {
        if (symbols == null || symbols.isEmpty()) return 0;
        List<String> upper = symbols.stream().map(String::toUpperCase).toList();
        int updated = assetRepository.setActiveBySymbols(upper, active);
        log.info("bulkSetActive: set is_active={} for {} symbols ({} updated)", active, upper.size(), updated);
        try {
            aggregatorClient.reloadAssets();
        } catch (Exception e) {
            log.warn("bulkSetActive: reloadAssets failed: {}", e.getMessage());
        }
        return updated;
    }

    /**
     * Activates all assets that have at least one enabled ticker in source_ticker_config.
     * Only assets currently inactive (is_active = false or null) are changed.
     * Returns the number of newly activated assets.
     */
    public int activateSyncedTokens() {
        Set<String> syncedSymbols = sourceTickerRepo.findAllEnabled()
                .stream()
                .map(t -> t.getSymbol().toUpperCase())
                .collect(Collectors.toSet());

        if (syncedSymbols.isEmpty()) {
            log.info("activateSyncedTokens: no enabled tickers found — nothing to activate");
            return 0;
        }

        int activated = assetRepository.activateBySymbols(syncedSymbols);
        log.info("activateSyncedTokens: activated {} assets from {} synced symbols", activated, syncedSymbols.size());
        return activated;
    }

    /**
     * Enables all source_ticker_config rows whose symbol (case-insensitive) matches an active
     * asset in the asset table. Optionally runs token discovery first to seed new rows from
     * exchange REST APIs (seeds with enabled=false, then this method enables them).
     *
     * Steps:
     *   1. (optional) POST /api/v1/discovery/seed → populates source_ticker_config for all
     *      exchange-available tokens with enabled=false
     *   2. Load all active asset symbols from asset table
     *   3. Enable all source_ticker_config rows whose symbol matches an active asset
     *   4. Reload aggregator so the new config takes effect immediately
     *
     * Returns { "enabled": N, "alreadyEnabled": M, "discovered": K } where:
     *   enabled       = rows newly set to enabled=true
     *   alreadyEnabled = rows that were already enabled (no-op)
     *   discovered    = total rows seeded by discovery step (0 if discoverFirst=false)
     */
    @Transactional("aggregatorTransactionManager")
    public Map<String, Integer> autoEnableAllTickers(boolean discoverFirst) {
        int discovered = 0;
        if (discoverFirst) {
            try {
                Map<String, Integer> seedResult = aggregatorClient.discoverAndSeedTokens();
                discovered = seedResult.values().stream().mapToInt(Integer::intValue).sum();
                log.info("autoEnableAllTickers: discovery seeded {} new ticker rows", discovered);
            } catch (Exception e) {
                log.warn("autoEnableAllTickers: discovery step failed (continuing): {}", e.getMessage());
            }
        }

        // Build set of active asset symbols (always uppercase in asset table)
        Set<String> activeSymbols = assetRepository.findAll()
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .map(a -> a.getSymbol().toUpperCase())
                .collect(Collectors.toSet());

        if (activeSymbols.isEmpty()) {
            log.info("autoEnableAllTickers: no active assets found — nothing to enable");
            return Map.of("enabled", 0, "alreadyEnabled", 0, "discovered", discovered);
        }

        // Find all ticker configs matching any active asset symbol
        List<AggregatorSourceTickerConfig> matching = sourceTickerRepo.findAll()
                .stream()
                .filter(t -> activeSymbols.contains(t.getSymbol().toUpperCase()))
                .toList();

        int alreadyEnabled = (int) matching.stream().filter(AggregatorSourceTickerConfig::isEnabled).count();

        List<AggregatorSourceTickerConfig> toEnable = matching.stream()
                .filter(t -> !t.isEnabled())
                .toList();

        if (!toEnable.isEmpty()) {
            toEnable.forEach(t -> t.setEnabled(true));
            sourceTickerRepo.saveAll(toEnable);
        }

        log.info("autoEnableAllTickers: enabled={}, alreadyEnabled={}, activeAssets={}, discovered={}",
                toEnable.size(), alreadyEnabled, activeSymbols.size(), discovered);

        try {
            aggregatorClient.reloadAssets();
        } catch (Exception e) {
            log.warn("autoEnableAllTickers: reloadAssets failed: {}", e.getMessage());
        }

        return Map.of("enabled", toEnable.size(), "alreadyEnabled", alreadyEnabled, "discovered", discovered);
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
