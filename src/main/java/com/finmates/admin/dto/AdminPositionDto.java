package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdminPositionDto {

    private Long id;
    private Long portfolioId;
    private Long userId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal avgEntryPrice;
    private BigDecimal totalInvested;
    private OffsetDateTime updatedAt;
}
