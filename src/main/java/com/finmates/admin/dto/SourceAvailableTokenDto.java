package com.finmates.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for a token available from a specific exchange REST API.
 * Used for admin browsing and selective import of tokens.
 * Copied from fm-crypto-aggregator.
 */
public class SourceAvailableTokenDto {
    @JsonProperty("symbol")
    public final String symbol;              // canonical: "BTC"

    @JsonProperty("exchangeSymbol")
    public final String exchangeSymbol;      // exchange format: "BTC/USD", "BTC-USD", etc.

    @JsonProperty("restSymbol")
    public final String restSymbol;          // nullable; Kraken only

    @JsonProperty("isNew")
    public final boolean isNew;              // true if not in source_ticker_config yet

    @JsonProperty("isCurrentlyEnabled")
    public final boolean isCurrentlyEnabled; // true if already in DB and is_enabled=true

    public SourceAvailableTokenDto(
            String symbol,
            String exchangeSymbol,
            String restSymbol,
            boolean isNew,
            boolean isCurrentlyEnabled) {
        this.symbol = symbol;
        this.exchangeSymbol = exchangeSymbol;
        this.restSymbol = restSymbol;
        this.isNew = isNew;
        this.isCurrentlyEnabled = isCurrentlyEnabled;
    }

    public String symbol() { return symbol; }
    public String exchangeSymbol() { return exchangeSymbol; }
    public String restSymbol() { return restSymbol; }
    public boolean isNew() { return isNew; }
    public boolean isCurrentlyEnabled() { return isCurrentlyEnabled; }
}
