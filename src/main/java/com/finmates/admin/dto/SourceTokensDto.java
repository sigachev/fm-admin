package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Available tokens from a single exchange source.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceTokensDto {
    private String sourceId;      // "hyperliquid", "kraken", etc.
    private String sourceName;    // "Hyperliquid", "Kraken", etc.
    private int priority;         // 1-5
    private boolean connected;    // Is this source currently connected?
    private List<String> tokens;  // List of available symbols on this source
}
