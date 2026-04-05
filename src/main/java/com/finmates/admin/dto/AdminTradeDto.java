package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdminTradeDto {

    private Long id;
    private Long portfolioId;
    private Long userId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal realizedPnl;
    private String status;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
}
