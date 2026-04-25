package com.finmates.admin.service;

import com.finmates.admin.dto.AdminPortfolioSummaryDto;
import com.finmates.admin.dto.AdminUserDetailDto;
import com.finmates.admin.dto.AdminUserDto;
import com.finmates.admin.entity.main.AdminUser;
import com.finmates.admin.repository.crypto.AdminPortfolioRepository;
import com.finmates.admin.repository.main.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository userRepository;
    private final AdminPortfolioRepository portfolioRepository;

    public Page<AdminUserDto> listUsers(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return userRepository
                    .findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)
                    .map(this::toDto);
        }
        return userRepository.findAll(pageable).map(this::toDto);
    }

    public AdminUserDetailDto getUser(Long id) {
        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));

        List<AdminPortfolioSummaryDto> portfolios = portfolioRepository.findByUserId(id).stream()
                .map(p -> new AdminPortfolioSummaryDto(
                        p.getId(), p.getName(), p.getType(),
                        p.isDefault(), p.isPublic()))
                .toList();

        AdminUserDetailDto dto = new AdminUserDetailDto();
        copyFields(user, dto);
        dto.setPortfolios(portfolios);
        return dto;
    }

    @Transactional("mainTransactionManager")
    public AdminUserDto toggleEnabled(Long id, boolean enabled) {
        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        user.setIsActive(enabled);
        user.setUpdatedAt(OffsetDateTime.now());
        return toDto(userRepository.save(user));
    }

    @Transactional("mainTransactionManager")
    public void deleteUser(Long id) {
        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        user.setDeletedAt(OffsetDateTime.now());
        user.setIsActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        log.info("Admin soft-deleted user id={} username={}", id, user.getUsername());
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AdminUserDto toDto(AdminUser u) {
        AdminUserDto dto = new AdminUserDto();
        copyFields(u, dto);
        return dto;
    }

    private void copyFields(AdminUser u, AdminUserDto dto) {
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setKeycloakId(u.getKeycloakId());
        dto.setEnabled(u.getIsActive() != null ? u.getIsActive() : false);
        dto.setDeletedAt(u.getDeletedAt());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setUpdatedAt(u.getUpdatedAt());
    }
}
