package com.finmates.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * Header data for the admin Tokens page: status counts, real exchange connection state, and the things that need
 * attention.
 *
 * @param total               number of tokens in the asset table
 * @param byStatus            count per {@code TokenStatus} name
 * @param sources             live connection state of every exchange, from the aggregator (empty if unreachable)
 * @param aggregatorReachable false when the aggregator could not be asked for prices / source state
 * @param duplicatePairs      one exchange pair enabled for more than one token (e.g. MATIC and POL on POL/USD)
 * @param notTrackedWithFeeds tokens with tracking off but feeds still enabled (subscribed for nothing)
 */
public record TokenOverviewDto(
        long total,
        Map<String, Long> byStatus,
        List<SourceStatusDto> sources,
        boolean aggregatorReachable,
        List<DuplicatePair> duplicatePairs,
        long notTrackedWithFeeds) {

    public record DuplicatePair(String sourceId, String exchangeSymbol, List<String> symbols) {
    }
}
