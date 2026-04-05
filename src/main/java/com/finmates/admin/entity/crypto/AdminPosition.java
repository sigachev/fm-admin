package com.finmates.admin.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read/write mapping of 'portfolio_positions' table in the crypto DB.
 * One row per unique (portfolio_id, symbol) pair.
 */
@Entity
@Table(name = "portfolio_positions")
@Getter
@Setter
@NoArgsConstructor
public class AdminPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice;

    @Column(name = "total_invested", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalInvested;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
