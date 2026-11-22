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
    /** Users not soft-deleted. */
    private long activeUsers;
    private long newUsersToday;
    private long newUsersThisWeek;
    /** Signups in the last 7 days (rolling, not calendar week). */
    private long newUsersLast7Days;
    /** Users who logged in during the last 7 days. */
    private long loggedInLast7Days;
    private long deletedUsers;
    private long totalNewsArticles;
    private long totalTokens;
    private long activeTokens;
}
