package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminPortfolioSummaryDto {

    private Long id;
    private String name;
    private String type;
    private BigDecimal cashBalance;
    private boolean isDefault;
    private boolean isPublic;
}
