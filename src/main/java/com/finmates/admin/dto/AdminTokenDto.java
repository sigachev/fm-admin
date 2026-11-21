package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for token/asset information returned by admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTokenDto {
    private Long id;
    private String symbol;
    private String name;
    private Boolean isActive;  // Use wrapper Boolean to handle nulls properly
    private Integer rank;
    private String logoUrl;
    private String description;
    private String website;
    private String twitter;
    private String whitepaper;
    private Instant createdAt;
    private Instant updatedAt;

    // ── Live status (computed per request; see TokenStatusService) ──────────
    /** {@link com.finmates.admin.service.TokenStatus} name: LIVE, DEGRADED, STALE, NO_FEED, NOT_TRACKED, UNKNOWN. */
    private String status;
    /** Exchanges with an ENABLED feed for this token (source_ticker_config.is_enabled), sorted. */
    private java.util.List<String> feeds;
    /** Every exchange that lists this token (enabled or not) - what the feed editor can offer. */
    private java.util.List<String> availableFeeds;
    /** How many of {@link #feeds} are connected right now. */
    private Integer connectedFeeds;
    /** Feeds that contributed to the last consolidated price. */
    private Integer contributingFeeds;
    private java.math.BigDecimal price;
    /** When the last consolidated price was computed. */
    private Instant lastPriceAt;
}
