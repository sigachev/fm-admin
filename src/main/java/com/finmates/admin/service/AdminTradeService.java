package com.finmates.admin.service;

import com.finmates.admin.dto.AdminTradeDto;
import com.finmates.admin.entity.crypto.AdminTrade;
import com.finmates.admin.repository.crypto.AdminTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminTradeService {

    private final AdminTradeRepository tradeRepository;

    public Page<AdminTradeDto> listTrades(Long portfolioId, String symbol, Long userId, Pageable pageable) {
        if (portfolioId != null && symbol != null) {
            return tradeRepository.findByPortfolioIdAndSymbol(portfolioId, symbol, pageable).map(this::toDto);
        }
        if (portfolioId != null) {
            return tradeRepository.findByPortfolioId(portfolioId, pageable).map(this::toDto);
        }
        if (userId != null) {
            return tradeRepository.findByUserId(userId, pageable).map(this::toDto);
        }
        if (symbol != null) {
            return tradeRepository.findBySymbol(symbol, pageable).map(this::toDto);
        }
        return tradeRepository.findAll(pageable).map(this::toDto);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AdminTradeDto toDto(AdminTrade t) {
        AdminTradeDto dto = new AdminTradeDto();
        dto.setId(t.getId());
        dto.setPortfolioId(t.getPortfolioId());
        dto.setUserId(t.getUserId());
        dto.setSymbol(t.getSymbol());
        dto.setSide(t.getSide());
        dto.setQuantity(t.getQuantity());
        dto.setEntryPrice(t.getEntryPrice());
        dto.setExitPrice(t.getExitPrice());
        dto.setRealizedPnl(t.getRealizedPnl());
        dto.setStatus(t.getStatus());
        dto.setOpenedAt(t.getOpenedAt());
        dto.setClosedAt(t.getClosedAt());
        return dto;
    }
}
