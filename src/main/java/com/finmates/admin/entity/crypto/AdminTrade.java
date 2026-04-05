package com.finmates.admin.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read mapping of 'portfolio_trades' table in the crypto DB.
 * side (BUY|SELL) and status (OPEN|CLOSED|CANCELLED) are stored as VARCHAR in the DB.
 */
@Entity
@Table(name = "portfolio_trades")
@Getter
@Setter
@NoArgsConstructor
public class AdminTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    /** BUY | SELL */
    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 20, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "fees", nullable = false, precision = 20, scale = 8)
    private BigDecimal fees;

    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    /** OPEN | CLOSED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
