package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.CreateTokenRequest;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.UpdateTokenRequest;
import com.finmates.admin.service.AdminTokenService;
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

    /**
     * List all tokens with optional search.
     */
    @GetMapping
    public Page<AdminTokenDto> listTokens(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return tokenService.listTokens(search, pageable);
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
     * Get exchange source health status.
     */
    @GetMapping("/sources/health")
    public List<SourceStatusDto> getSourceHealth() {
        return tokenService.getSourceHealth();
    }
}
