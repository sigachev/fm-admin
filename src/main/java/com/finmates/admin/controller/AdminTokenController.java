package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.AvailableTokenDto;
import com.finmates.admin.dto.CreateTokenRequest;
import com.finmates.admin.dto.SourceTokensDto;
import com.finmates.admin.dto.UpdateTokenRequest;
import com.finmates.admin.service.AdminTokenService;
import com.finmates.admin.service.TokenDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin API for managing crypto tokens/assets.
 * All endpoints require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/tokens")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTokenController {

    private final AdminTokenService tokenService;
    private final TokenDiscoveryService discoveryService;

    /**
     * List all tokens with optional search and source filter.
     * sourceId filters to tokens that have an enabled ticker for that exchange.
     */
    @GetMapping
    public Page<AdminTokenDto> listTokens(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceId,
            @PageableDefault(size = 20) Pageable pageable) {
        return tokenService.listTokens(search, sourceId, pageable);
    }

    /**
     * Get a single token by symbol.
     */
    @GetMapping("/{symbol}")
    public AdminTokenDto getToken(@PathVariable String symbol) {
        return tokenService.getToken(symbol);
    }

    /**
     * Create a new token.
     */
    @PostMapping
    public ResponseEntity<AdminTokenDto> createToken(@RequestBody CreateTokenRequest request) {
        AdminTokenDto token = tokenService.createToken(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    /**
     * Update token metadata (name, rank).
     */
    @PutMapping("/{symbol}")
    public AdminTokenDto updateToken(
            @PathVariable String symbol,
            @RequestBody UpdateTokenRequest request) {
        return tokenService.updateToken(symbol, request);
    }

    /**
     * Toggle token's active/inactive status.
     */
    @PatchMapping("/{symbol}/active")
    public AdminTokenDto toggleActive(@PathVariable String symbol) {
        return tokenService.toggleActive(symbol);
    }

    /**
     * Delete a token.
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteToken(@PathVariable String symbol) {
        tokenService.deleteToken(symbol);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get available tokens per exchange source.
     * Useful for discovering which tokens each source supports.
     */
    @GetMapping("/discovery/sources")
    public List<SourceTokensDto> getSourceTokens() {
        return discoveryService.getSourceTokens();
    }

    /**
     * Get all unique available tokens across all sources.
     * Marks which ones are already in the system.
     */
    @GetMapping("/discovery/available")
    public List<AvailableTokenDto> getAvailableTokens() {
        return discoveryService.getAllAvailableTokens();
    }

    /**
     * Add a new token to the system from available sources.
     */
    @PostMapping("/discovery/add")
    public ResponseEntity<AdminTokenDto> addTokenFromDiscovery(
            @RequestParam String symbol,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer rank) {
        discoveryService.addToken(symbol, name, rank);
        discoveryService.reloadAggregatorAssets();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tokenService.getToken(symbol));
    }

    /**
     * Add multiple tokens to the asset table in one call.
     * Skips tokens already in the table. Reloads aggregator after.
     * Body: { "symbols": ["AAVE", "LINK", ...] }
     */
    @PostMapping("/discovery/add/bulk")
    public ResponseEntity<java.util.Map<String, Object>> addTokensBulk(
            @RequestBody java.util.Map<String, List<String>> body) {
        List<String> symbols = body.getOrDefault("symbols", List.of());
        int added = discoveryService.addTokensBulk(symbols);
        if (added > 0) {
            discoveryService.reloadAggregatorAssets();
        }
        return ResponseEntity.ok(java.util.Map.of("added", added, "requested", symbols.size()));
    }

    /**
     * Trigger a metadata fetch from OKX for all tracked assets.
     * Updates name and logoUrl in the asset table for recognised symbols.
     * Returns { updated: N, notFound: M }.
     */
    @PostMapping("/metadata/fetch")
    public ResponseEntity<java.util.Map<String, Integer>> fetchMetadataFromOkx() {
        java.util.Map<String, Integer> result = tokenService.fetchMetadataFromOkx();
        return ResponseEntity.ok(result);
    }

    /**
     * Detect case-insensitive duplicate symbols in the asset table.
     * Returns a map of UPPER(symbol) → list of duplicate rows.
     * Safe to call at any time — read-only.
     */
    @GetMapping("/duplicates")
    public java.util.Map<String, java.util.List<AdminTokenDto>> getDuplicates() {
        return tokenService.findDuplicates();
    }

    /**
     * Remove case-insensitive duplicate assets.
     * Keeps the row with the lowest ID per symbol group (oldest = canonical).
     * Updates source_ticker_config references before deleting.
     * Returns { removed: N }.
     */
    @DeleteMapping("/duplicates")
    public ResponseEntity<java.util.Map<String, Integer>> removeDuplicates() {
        int removed = tokenService.removeDuplicates();
        return ResponseEntity.ok(java.util.Map.of("removed", removed));
    }
}
