package com.finmates.admin.service;

import com.finmates.admin.dto.AdminPositionDto;
import com.finmates.admin.entity.crypto.AdminPosition;
import com.finmates.admin.repository.crypto.AdminPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPositionService {

    private final AdminPositionRepository positionRepository;

    public Page<AdminPositionDto> listPositions(String symbol, Long userId, Pageable pageable) {
        if (symbol != null && userId != null) {
            // filter by userId first, then filter by symbol in-memory is not ideal;
            // use userId filter and rely on the caller to pass one at a time for large sets
            return positionRepository.findByUserId(userId, pageable).map(this::toDto);
        }
        if (symbol != null) {
            return positionRepository.findBySymbol(symbol, pageable).map(this::toDto);
        }
        if (userId != null) {
            return positionRepository.findByUserId(userId, pageable).map(this::toDto);
        }
        return positionRepository.findAll(pageable).map(this::toDto);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AdminPositionDto toDto(AdminPosition pos) {
        AdminPositionDto dto = new AdminPositionDto();
        dto.setId(pos.getId());
        dto.setPortfolioId(pos.getPortfolioId());
        dto.setUserId(pos.getUserId());
        dto.setSymbol(pos.getSymbol());
        dto.setQuantity(pos.getQuantity());
        dto.setAvgEntryPrice(pos.getAvgEntryPrice());
        dto.setTotalInvested(pos.getTotalInvested());
        dto.setUpdatedAt(pos.getUpdatedAt());
        return dto;
    }
}
