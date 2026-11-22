package com.finmates.admin.service;

import com.finmates.admin.dto.AdminStatsDto;
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
    private final AdminNewsRepository newsRepository;
    private final AdminTokenService tokenService;

    public AdminStatsDto getStats() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1L).truncatedTo(ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long newUsersThisWeek = userRepository.countByCreatedAtAfter(startOfWeek);
        long newUsersLast7Days = userRepository.countByCreatedAtAfter(now.minusDays(7));
        long loggedInLast7Days = userRepository.countLoggedInWithinDays(7);
        long deletedUsers = userRepository.countByDeletedAtIsNotNull();
        long notDeleted = userRepository.countByDeletedAtIsNull();
        long totalNewsArticles = newsRepository.count();
        long totalTokens = tokenService.getTotalTokenCount();
        long activeTokens = tokenService.getActiveTokenCount();

        // Named setters, not the positional constructor: 10 longs in a row is how a field got passed twice.
        AdminStatsDto dto = new AdminStatsDto();
        dto.setTotalUsers(totalUsers);
        dto.setActiveUsers(notDeleted);
        dto.setNewUsersToday(newUsersToday);
        dto.setNewUsersThisWeek(newUsersThisWeek);
        dto.setNewUsersLast7Days(newUsersLast7Days);
        dto.setLoggedInLast7Days(loggedInLast7Days);
        dto.setDeletedUsers(deletedUsers);
        dto.setTotalNewsArticles(totalNewsArticles);
        dto.setTotalTokens(totalTokens);
        dto.setActiveTokens(activeTokens);
        return dto;
    }
}
