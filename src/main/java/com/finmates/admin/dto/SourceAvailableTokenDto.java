package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO for a token available from a specific exchange REST API.
 * Used for admin browsing and selective import of tokens.
 * Copied from fm-crypto-aggregator.
 */
@Getter
@AllArgsConstructor
public class SourceAvailableTokenDto {
    private final String symbol;              // canonical: "BTC"
    private final String exchangeSymbol;      // exchange format: "BTC/USD", "BTC-USD", etc.
    private final String restSymbol;          // nullable; Kraken only
    private final boolean isNew;              // true if not in source_ticker_config yet
    private final boolean isCurrentlyEnabled; // true if already in DB and is_enabled=true
}
