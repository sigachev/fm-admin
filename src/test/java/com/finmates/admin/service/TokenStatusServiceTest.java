package com.finmates.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.finmates.admin.dto.LivePriceDto;
import com.finmates.admin.dto.TokenOverviewDto;
import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenStatusServiceTest {

    private static LivePriceDto price(String feedStatus) {
        return new LivePriceDto("BTC", new BigDecimal("97000"), "kraken,okx,coinbase", Instant.now(), 3, feedStatus);
    }

    @Test
    void notTrackedWinsOverEverything() {
        assertThat(TokenStatusService.statusOf(false, 3, true, price("HEALTHY"))).isEqualTo(TokenStatus.NOT_TRACKED);
        assertThat(TokenStatusService.statusOf(false, 0, false, null)).isEqualTo(TokenStatus.NOT_TRACKED);
    }

    @Test
    void trackedWithoutFeedsIsNoFeed() {
        // The USDT case: "Tracked" with no exchange behind it can never get a price.
        assertThat(TokenStatusService.statusOf(true, 0, true, null)).isEqualTo(TokenStatus.NO_FEED);
        assertThat(TokenStatusService.statusOf(true, 0, false, null)).isEqualTo(TokenStatus.NO_FEED);
    }

    @Test
    void unreachableAggregatorIsUnknownNotStale() {
        assertThat(TokenStatusService.statusOf(true, 2, false, null)).isEqualTo(TokenStatus.UNKNOWN);
    }

    @Test
    void liveStateFollowsTheAggregatorsFeedStatus() {
        assertThat(TokenStatusService.statusOf(true, 3, true, price("HEALTHY"))).isEqualTo(TokenStatus.LIVE);
        assertThat(TokenStatusService.statusOf(true, 3, true, price("DEGRADED"))).isEqualTo(TokenStatus.DEGRADED);
        assertThat(TokenStatusService.statusOf(true, 3, true, price("STALE"))).isEqualTo(TokenStatus.STALE);
        assertThat(TokenStatusService.statusOf(true, 3, true, null)).isEqualTo(TokenStatus.STALE);
    }

    private static AggregatorSourceTickerConfig row(String source, String symbol, String pair) {
        AggregatorSourceTickerConfig c = new AggregatorSourceTickerConfig();
        c.setSourceId(source);
        c.setSymbol(symbol);
        c.setExchangeSymbol(pair);
        c.setEnabled(true);
        return c;
    }

    @Test
    void findsOnePairEnabledForTwoTokens() {
        List<TokenOverviewDto.DuplicatePair> dupes = TokenStatusService.duplicatePairs(List.of(
                row("kraken", "MATIC", "POL/USD"), row("kraken", "POL", "POL/USD"),
                row("coinbase", "MATIC", "POL-USD"), row("coinbase", "POL", "POL-USD"),
                row("kraken", "BTC", "BTC/USD"),
                // the same token on two exchanges is NOT a duplicate
                row("okx", "BTC", "BTC-USDT")));

        assertThat(dupes).extracting(TokenOverviewDto.DuplicatePair::sourceId).containsExactly("coinbase", "kraken");
        assertThat(dupes).allSatisfy(d -> assertThat(d.symbols()).containsExactly("MATIC", "POL"));
    }
}
