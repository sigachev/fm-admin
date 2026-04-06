package com.finmates.admin.entity.aggregator;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "source_ticker_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AggregatorSourceTickerConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, length = 50)
    private String sourceId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "exchange_symbol", nullable = false, length = 50)
    private String exchangeSymbol;

    @Column(name = "rest_symbol", length = 50)
    private String restSymbol;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
