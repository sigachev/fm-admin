package com.finmates.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry of the aggregator's {@code GET /api/v1/prices/current} map: the consolidated (median) price and its
 * freshness. {@code feedStatusSummary} is HEALTHY / DEGRADED / STALE, derived by the aggregator at read time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LivePriceDto(
        String symbol,
        BigDecimal price,
        String sourceId,
        Instant consolidatedAt,
        Integer contributingFeedCount,
        String feedStatusSummary) {
}
