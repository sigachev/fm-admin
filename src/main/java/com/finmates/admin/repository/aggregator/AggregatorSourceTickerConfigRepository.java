package com.finmates.admin.repository.aggregator;

import com.finmates.admin.entity.aggregator.AggregatorSourceTickerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AggregatorSourceTickerConfigRepository
        extends JpaRepository<AggregatorSourceTickerConfig, Long> {
    List<AggregatorSourceTickerConfig> findBySourceId(String sourceId);
    Optional<AggregatorSourceTickerConfig> findBySourceIdAndSymbol(String sourceId, String symbol);
    Optional<AggregatorSourceTickerConfig> findBySourceIdAndSymbolIgnoreCase(String sourceId, String symbol);
}
