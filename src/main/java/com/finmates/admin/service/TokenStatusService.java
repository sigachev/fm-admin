package com.finmates.admin.service;

import com.finmates.admin.dto.AdminTokenDto;
import com.finmates.admin.dto.LivePriceDto;
import com.finmates.admin.dto.SourceStatusDto;
import com.finmates.admin.dto.TokenOverviewDto;
import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
import com.finmates.admin.repository.aggregator.AggregatorSourceTickerConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes each token's {@link TokenStatus} from three inputs: tracking (asset.is_active), its enabled exchange feeds
 * (source_ticker_config), and the aggregator's live consolidated price. The live data is fetched once and cached for a
 * few seconds, so a page of 20 tokens costs two aggregator calls, not forty.
 */
@Service
@RequiredArgsConstructor
public class TokenStatusService {

    private static final Duration LIVE_CACHE = Duration.ofSeconds(5);

    private final AggregatorClient aggregatorClient;
    private final AggregatorSourceTickerConfigRepository tickerRepo;

    /** Live aggregator state. {@code prices == null} means the aggregator was unreachable. */
    public record Live(Map<String, LivePriceDto> prices, List<SourceStatusDto> sources, Instant fetchedAt) {
        boolean reachable() {
            return prices != null;
        }

        Set<String> connectedSources() {
            return sources.stream().filter(SourceStatusDto::isConnected).map(SourceStatusDto::getSourceId)
                    .collect(Collectors.toSet());
        }
    }

    /** Feeds per token, keyed by UPPER-case symbol (asset.symbol is upper-case; ticker rows can be kSHIB). */
    public record Feeds(Map<String, List<String>> enabled, Map<String, List<String>> available) {
    }

    private volatile Live cached;

    public Live live() {
        Live c = cached;
        if (c != null && Duration.between(c.fetchedAt(), Instant.now()).compareTo(LIVE_CACHE) < 0) {
            return c;
        }
        Map<String, LivePriceDto> prices = aggregatorClient.getCurrentPrices();
        List<SourceStatusDto> sources = prices == null ? List.of() : aggregatorClient.getSourceHealth();
        Live fresh = new Live(prices, sources, Instant.now());
        cached = fresh;
        return fresh;
    }

    public Feeds feeds() {
        List<AggregatorSourceTickerConfig> rows = tickerRepo.findAll();
        Map<String, Set<String>> enabled = new TreeMap<>();
        Map<String, Set<String>> available = new TreeMap<>();
        for (AggregatorSourceTickerConfig r : rows) {
            String sym = r.getSymbol().toUpperCase();
            available.computeIfAbsent(sym, k -> new TreeSet<>()).add(r.getSourceId());
            if (r.isEnabled()) {
                enabled.computeIfAbsent(sym, k -> new TreeSet<>()).add(r.getSourceId());
            }
        }
        return new Feeds(toLists(enabled), toLists(available));
    }

    /** Fill the status fields of each DTO in place. */
    public void enrich(Collection<AdminTokenDto> tokens, Live live, Feeds feeds) {
        Set<String> connected = live.connectedSources();
        for (AdminTokenDto t : tokens) {
            String sym = t.getSymbol().toUpperCase();
            List<String> enabled = feeds.enabled().getOrDefault(sym, List.of());
            LivePriceDto price = live.reachable() ? live.prices().get(sym) : null;
            t.setFeeds(enabled);
            t.setAvailableFeeds(feeds.available().getOrDefault(sym, List.of()));
            t.setConnectedFeeds((int) enabled.stream().filter(connected::contains).count());
            if (price != null) {
                t.setPrice(price.price());
                t.setLastPriceAt(price.consolidatedAt());
                t.setContributingFeeds(price.contributingFeedCount());
            }
            t.setStatus(statusOf(Boolean.TRUE.equals(t.getIsActive()), enabled.size(), live.reachable(), price).name());
        }
    }

    /**
     * The status rules, in order:
     * not tracked -> NOT_TRACKED; no enabled feed -> NO_FEED; aggregator unreachable -> UNKNOWN;
     * no price or aggregator says STALE -> STALE; aggregator says DEGRADED -> DEGRADED; otherwise LIVE.
     */
    static TokenStatus statusOf(boolean tracked, int enabledFeeds, boolean aggregatorReachable, LivePriceDto price) {
        if (!tracked) {
            return TokenStatus.NOT_TRACKED;
        }
        if (enabledFeeds == 0) {
            return TokenStatus.NO_FEED;
        }
        if (!aggregatorReachable) {
            return TokenStatus.UNKNOWN;
        }
        if (price == null || price.price() == null || "STALE".equals(price.feedStatusSummary())) {
            return TokenStatus.STALE;
        }
        if ("DEGRADED".equals(price.feedStatusSummary())) {
            return TokenStatus.DEGRADED;
        }
        return TokenStatus.LIVE;
    }

    /** Enabled rows that share one exchange pair across different tokens (MATIC + POL on POL/USD). */
    public List<TokenOverviewDto.DuplicatePair> duplicatePairs() {
        return duplicatePairs(tickerRepo.findAllEnabled());
    }

    static List<TokenOverviewDto.DuplicatePair> duplicatePairs(List<AggregatorSourceTickerConfig> enabledRows) {
        Map<String, Set<String>> byPair = new TreeMap<>();
        Map<String, AggregatorSourceTickerConfig> sample = new TreeMap<>();
        for (AggregatorSourceTickerConfig r : enabledRows) {
            String key = r.getSourceId() + "|" + r.getExchangeSymbol().toUpperCase();
            byPair.computeIfAbsent(key, k -> new TreeSet<>()).add(r.getSymbol().toUpperCase());
            sample.putIfAbsent(key, r);
        }
        List<TokenOverviewDto.DuplicatePair> out = new ArrayList<>();
        byPair.forEach((key, symbols) -> {
            if (symbols.size() > 1) {
                AggregatorSourceTickerConfig r = sample.get(key);
                out.add(new TokenOverviewDto.DuplicatePair(r.getSourceId(), r.getExchangeSymbol(),
                        new ArrayList<>(symbols)));
            }
        });
        return out;
    }

    private static Map<String, List<String>> toLists(Map<String, Set<String>> in) {
        Map<String, List<String>> out = new TreeMap<>();
        in.forEach((k, v) -> out.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
        return out;
    }
}
