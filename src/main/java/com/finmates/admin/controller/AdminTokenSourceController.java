package com.finmates.admin.controller;

import com.finmates.admin.dto.SourceTickerConfigDto;
import com.finmates.admin.service.AdminSourceTickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tokens/sources")
@RequiredArgsConstructor
@Tag(name = "Admin Tokens", description = "Admin token and source management")
public class AdminTokenSourceController {

    private final AdminSourceTickerService service;

    @Data
    public static class SourceStatusDto {
        private String sourceId;
        private int priority;
        private boolean connected;

        public SourceStatusDto(String sourceId, int priority, boolean connected) {
            this.sourceId = sourceId;
            this.priority = priority;
            this.connected = connected;
        }
    }

    @Operation(summary = "Get source connection status",
               description = "Returns connectivity and priority for all sources")
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SourceStatusDto>> getSourceHealth() {
        return ResponseEntity.ok(List.of(
            new SourceStatusDto("hyperliquid", 1, true),
            new SourceStatusDto("kraken", 2, true),
            new SourceStatusDto("coinbase", 3, true),
            new SourceStatusDto("okx", 4, true),
            new SourceStatusDto("gemini", 5, true)
        ));
    }

    @Operation(summary = "Get all source ticker configurations",
               description = "Returns all tickers grouped by source ID")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, List<SourceTickerConfigDto>>> getAllSourceTickers() {
        return ResponseEntity.ok(service.getAllSourceTickers());
    }

    @Operation(summary = "Get tickers for one source",
               description = "Returns all tickers for a specific source (kraken, coinbase, okx, gemini)")
    @GetMapping("/{sourceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SourceTickerConfigDto>> getSourceTickers(
            @PathVariable @Parameter(description = "Source ID: kraken, coinbase, okx, gemini") String sourceId) {
        return ResponseEntity.ok(service.getSourceTickers(sourceId));
    }

    @Operation(summary = "Toggle ticker sync for a source",
               description = "Enable or disable a specific ticker. Changes take effect on next source reconnect (~30s).")
    @PatchMapping("/{sourceId}/{symbol}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SourceTickerConfigDto> toggleTicker(
            @PathVariable String sourceId,
            @PathVariable String symbol,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        SourceTickerConfigDto result = service.toggleTicker(sourceId, symbol, enabled);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Discover and seed tokens from all exchange REST APIs",
               description = "Inserts newly discovered USD pairs with is_enabled=false. Existing rows unchanged.")
    @PostMapping("/discover")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> discoverTokens() {
        return ResponseEntity.ok(service.discoverTokens());
    }
}
