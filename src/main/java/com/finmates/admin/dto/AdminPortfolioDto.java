package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdminPortfolioDto {

    private Long id;
    private Long userId;
    private String username;
    private String name;
    private String type;
    private String provider;
    private BigDecimal cashBalance;
    private BigDecimal initialBalance;
    private boolean isDefault;
    private boolean isPublic;
    private OffsetDateTime createdAt;
}
