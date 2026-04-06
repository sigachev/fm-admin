package com.finmates.admin.dto;

import java.time.Instant;

public record SourceTickerConfigDto(
    Long id,
    String sourceId,
    String symbol,
    String exchangeSymbol,
    String restSymbol,
    boolean enabled,
    Instant updatedAt
) {}
