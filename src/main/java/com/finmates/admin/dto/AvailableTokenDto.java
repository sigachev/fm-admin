package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A unique token available across one or more exchange sources.
 * Marked if already added to the asset table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableTokenDto {
    private String symbol;        // "BTC", "ETH", etc.
    private List<String> sources; // Sources that have this token: ["hyperliquid", "kraken", ...]
    private boolean alreadyAdded; // true if token exists in asset table
}
