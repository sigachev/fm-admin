package com.finmates.admin.service;

/**
 * The single, computed answer to "is this token getting a price?" shown on the admin Tokens page.
 * Derived from: asset.is_active (tracking on/off), the enabled exchange feeds in source_ticker_config, and the
 * aggregator's live price for the symbol.
 */
public enum TokenStatus {
    /** Tracked, price fresh, 3+ feeds agree. */
    LIVE,
    /** Tracked and priced, but fewer than 3 feeds contribute or a contributing exchange is disconnected. */
    DEGRADED,
    /** Tracked and has feeds, but no fresh price (none received yet, or older than the stale threshold). */
    STALE,
    /** Tracked but no exchange feed is enabled, so it can never get a price. */
    NO_FEED,
    /** Tracking is off. */
    NOT_TRACKED,
    /** The aggregator could not be reached, so the live state is unknown. */
    UNKNOWN
}
