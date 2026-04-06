package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for exchange source status from fm-crypto-aggregator.
 * Represents connection status of data sources (Hyperliquid, Kraken, etc).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceStatusDto {
    private String sourceId;
    private int priority;
    private boolean connected;
}
