package com.finmates.admin.service;

import com.finmates.admin.dto.AdminPortfolioDetailDto;
import com.finmates.admin.dto.AdminPortfolioDto;
import com.finmates.admin.dto.AdminPositionDto;
import com.finmates.admin.entity.crypto.AdminPortfolio;
import com.finmates.admin.entity.crypto.AdminPosition;
import com.finmates.admin.repository.crypto.AdminPortfolioRepository;
import com.finmates.admin.repository.crypto.AdminPositionRepository;
import com.finmates.admin.repository.crypto.AdminTradeRepository;
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
public class AdminPortfolioService {

    private final AdminPortfolioRepository portfolioRepository;
    private final AdminPositionRepository positionRepository;
    private final AdminTradeRepository tradeRepository;

    public Page<AdminPortfolioDto> listPortfolios(Long userId, Pageable pageable) {
        if (userId != null) {
            return portfolioRepository.findByUserId(userId, pageable).map(this::toDto);
        }
        return portfolioRepository.findAll(pageable).map(this::toDto);
    }

    public AdminPortfolioDetailDto getPortfolio(Long id) {
        AdminPortfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + id));

        List<AdminPositionDto> positions = positionRepository.findByPortfolioId(id).stream()
                .map(this::toPositionDto)
                .toList();
        long tradeCount = tradeRepository.findByPortfolioId(id, Pageable.unpaged()).getTotalElements();

        AdminPortfolioDetailDto dto = new AdminPortfolioDetailDto();
        copyFields(portfolio, dto);
        dto.setPositions(positions);
        dto.setTradeCount(tradeCount);
        return dto;
    }

    @Transactional("cryptoTransactionManager")
    public void deletePosition(Long portfolioId, String symbol) {
        AdminPosition position = positionRepository.findByPortfolioIdAndSymbol(portfolioId, symbol)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, symbol + " position not found in portfolio " + portfolioId));
        positionRepository.delete(position);
        log.info("Admin deleted {} position from portfolio {}", symbol, portfolioId);
    }

    @Transactional("cryptoTransactionManager")
    public AdminPortfolioDto resetPortfolio(Long id) {
        AdminPortfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + id));
        positionRepository.deleteByPortfolioId(id);
        portfolio.setCashBalance(portfolio.getInitialBalance());
        portfolio.setUpdatedAt(OffsetDateTime.now());
        log.info("Admin reset portfolio id={} userId={}", id, portfolio.getUserId());
        return toDto(portfolioRepository.save(portfolio));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AdminPortfolioDto toDto(AdminPortfolio p) {
        AdminPortfolioDto dto = new AdminPortfolioDto();
        copyFields(p, dto);
        return dto;
    }

    private void copyFields(AdminPortfolio p, AdminPortfolioDto dto) {
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setUsername(p.getUsername());
        dto.setName(p.getName());
        dto.setType(p.getType());
        dto.setProvider(p.getProvider());
        dto.setCashBalance(p.getCashBalance());
        dto.setInitialBalance(p.getInitialBalance());
        dto.setDefault(p.isDefault());
        dto.setPublic(p.isPublic());
        dto.setCreatedAt(p.getCreatedAt());
    }

    private AdminPositionDto toPositionDto(AdminPosition pos) {
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
