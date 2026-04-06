package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {

    private long totalUsers;
    private long activeUsers;
    private long newUsersToday;
    private long newUsersThisWeek;
    private long totalPortfolios;
    private long totalTrades;
    private long totalPositions;
    private long totalNewsArticles;
    private long totalTokens;
    private long activeTokens;
}
