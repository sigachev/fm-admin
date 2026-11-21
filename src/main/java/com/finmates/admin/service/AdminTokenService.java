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
    private final TokenStatusService tokenStatusService;
    private final AggregatorReloader aggregatorReloader;

    /**
     * List tokens with optional search, exchange filter and status filter, each with its computed live status.
     * When {@code sourceId} is set, only tokens with an enabled feed on that exchange are returned. When {@code status}
     * is set the status is computed for every matching token and the page is cut in memory (about a thousand rows).
     */
    public Page<AdminTokenDto> listTokens(String search, String sourceId, String status, Pageable pageable) {
        TokenStatusService.Live live = tokenStatusService.live();
        TokenStatusService.Feeds feeds = tokenStatusService.feeds();

        if (status == null || status.isBlank()) {
            Page<AdminTokenDto> page = findAssets(search, sourceId, pageable).map(this::toDto);
            tokenStatusService.enrich(page.getContent(), live, feeds);
            return page;
        }

        Set<String> wanted = java.util.Arrays.stream(status.split(","))
                .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        Pageable all = org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE, pageable.getSort());
        List<AdminTokenDto> rows = findAssets(search, sourceId, all).map(this::toDto).getContent();
        rows = new ArrayList<>(rows);
        tokenStatusService.enrich(rows, live, feeds);
        List<AdminTokenDto> filtered = rows.stream().filter(t -> wanted.contains(t.getStatus())).toList();
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new org.springframework.data.domain.PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
    }

    private Page<AdminAsset> findAssets(String search, String sourceId, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        if (sourceId != null && !sourceId.isBlank()) {
            // Enabled symbols for this source, upper-cased to match the asset table
            Set<String> symbols = sourceTickerRepo.findEnabledBySourceId(sourceId)
                    .stream()
                    .map(t -> t.getSymbol().toUpperCase())
                    .collect(Collectors.toSet());
            if (symbols.isEmpty()) {
                return Page.empty(pageable);
            }
            return hasSearch
                    ? assetRepository.findBySymbolInAndSearchTerm(symbols, search.trim(), pageable)
                    : assetRepository.findBySymbolIn(symbols, pageable);
        }
        if (hasSearch) {
            String q = search.trim();
            return assetRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
        }
        return assetRepository.findAll(pageable);
    }

    /**
     * Header data for the Tokens page: counts per status, the real exchange connection state, duplicate pairs and
     * tokens subscribed for nothing.
     */
    public com.finmates.admin.dto.TokenOverviewDto overview() {
        TokenStatusService.Live live = tokenStatusService.live();
        TokenStatusService.Feeds feeds = tokenStatusService.feeds();
        List<AdminTokenDto> all = new ArrayList<>(assetRepository.findAll().stream().map(this::toDto).toList());
        tokenStatusService.enrich(all, live, feeds);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TokenStatus st : TokenStatus.values()) {
            byStatus.put(st.name(), 0L);
        }
        all.forEach(t -> byStatus.merge(t.getStatus(), 1L, Long::sum));
        long notTrackedWithFeeds = all.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsActive()) && !t.getFeeds().isEmpty()).count();

        return new com.finmates.admin.dto.TokenOverviewDto(all.size(), byStatus, live.sources(),
                live.prices() != null, tokenStatusService.duplicatePairs(), notTrackedWithFeeds);
    }

    /**
     * Turn price tracking on or off. Turning it ON for a token with no enabled feed also enables every exchange that
     * lists it, so one click gives a working price; the result says which feeds were switched on. The aggregator is
     * reloaded after commit, so the change applies without a restart.
     */
    public com.finmates.admin.dto.TrackingResultDto setTracking(String symbol, boolean tracked) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));
        asset.setIsActive(tracked);
        asset.setUpdatedAt(Instant.now());
        assetRepository.save(asset);

        List<String> autoEnabled = new ArrayList<>();
        if (tracked) {
            List<AggregatorSourceTickerConfig> rows = sourceTickerRepo.findBySymbolIgnoreCase(symbol);
            if (rows.stream().noneMatch(AggregatorSourceTickerConfig::isEnabled)) {
                for (AggregatorSourceTickerConfig r : rows) {
                    r.setEnabled(true);
                    r.setUpdatedAt(Instant.now());
                    autoEnabled.add(r.getSourceId());
                }
                sourceTickerRepo.saveAll(rows);
            }
        }
        log.info("Tracking {} for {} (auto-enabled feeds: {})", tracked ? "ON" : "OFF", symbol, autoEnabled);
        aggregatorReloader.reloadAfterCommit("tracking " + (tracked ? "on" : "off") + " for " + symbol);

        AdminTokenDto dto = toDto(asset);
        // Reflect the change immediately; live price fields fill in once the aggregator has a tick.
        TokenStatusService.Feeds feeds = tokenStatusService.feeds();
        if (!autoEnabled.isEmpty()) {
            List<String> enabledNow = new ArrayList<>(feeds.enabled().getOrDefault(symbol.toUpperCase(), List.of()));
            autoEnabled.stream().filter(f -> !enabledNow.contains(f)).forEach(enabledNow::add);
            java.util.Collections.sort(enabledNow);
            Map<String, List<String>> enabled = new java.util.TreeMap<>(feeds.enabled());
            enabled.put(symbol.toUpperCase(), enabledNow);
            feeds = new TokenStatusService.Feeds(enabled, feeds.available());
        }
        tokenStatusService.enrich(List.of(dto), tokenStatusService.live(), feeds);
        return new com.finmates.admin.dto.TrackingResultDto(dto, autoEnabled);
    }

    /**
     * Get a token by symbol.
     */
    public AdminTokenDto getToken(String symbol) {
        AdminAsset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + symbol));
        AdminTokenDto dto = toDto(asset);
        tokenStatusService.enrich(List.of(dto), tokenStatusService.live(), tokenStatusService.feeds());
        return dto;
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

        aggregatorReloader.reloadAfterCommit("token created");

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

        aggregatorReloader.reloadAfterCommit("tracking toggled");

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
        aggregatorReloader.reloadAfterCommit("bulk set active");
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

        aggregatorReloader.reloadAfterCommit("auto-enable tickers");

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
