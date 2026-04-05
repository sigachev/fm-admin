package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminTradeDto;
import com.finmates.admin.service.AdminTradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/trades")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTradeController {

    private final AdminTradeService tradeService;

    @GetMapping
    public Page<AdminTradeDto> listTrades(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        return tradeService.listTrades(portfolioId, symbol, userId, pageable);
    }
}
