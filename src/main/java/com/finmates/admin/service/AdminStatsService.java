package com.finmates.admin.service;

import com.finmates.admin.dto.AdminStatsDto;
import com.finmates.admin.repository.crypto.AdminPortfolioRepository;
import com.finmates.admin.repository.crypto.AdminPositionRepository;
import com.finmates.admin.repository.crypto.AdminTradeRepository;
import com.finmates.admin.repository.main.AdminNewsRepository;
import com.finmates.admin.repository.main.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminUserRepository userRepository;
    private final AdminPortfolioRepository portfolioRepository;
    private final AdminTradeRepository tradeRepository;
    private final AdminPositionRepository positionRepository;
    private final AdminNewsRepository newsRepository;
    private final AdminTokenService tokenService;

    public AdminStatsDto getStats() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1L).truncatedTo(ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long newUsersThisWeek = userRepository.countByCreatedAtAfter(startOfWeek);
        long totalPortfolios = portfolioRepository.count();
        long totalTrades = tradeRepository.count();
        long totalPositions = positionRepository.count();
        long totalNewsArticles = newsRepository.count();
        long totalTokens = tokenService.getTotalTokenCount();
        long activeTokens = tokenService.getActiveTokenCount();

        return new AdminStatsDto(
                totalUsers,
                totalUsers,          // activeUsers: same as totalUsers (no separate active count query)
                newUsersToday,
                newUsersThisWeek,
                totalPortfolios,
                totalTrades,
                totalPositions,
                totalNewsArticles,
                totalTokens,
                activeTokens
        );
    }
}
