package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminPortfolioDetailDto;
import com.finmates.admin.dto.AdminPortfolioDto;
import com.finmates.admin.service.AdminPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/portfolios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPortfolioController {

    private final AdminPortfolioService portfolioService;

    @GetMapping
    public Page<AdminPortfolioDto> listPortfolios(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return portfolioService.listPortfolios(userId, pageable);
    }

    @GetMapping("/{id}")
    public AdminPortfolioDetailDto getPortfolio(@PathVariable Long id) {
        return portfolioService.getPortfolio(id);
    }

    @DeleteMapping("/{id}/positions/{symbol}")
    public ResponseEntity<Void> deletePosition(
            @PathVariable Long id,
            @PathVariable String symbol) {
        portfolioService.deletePosition(id, symbol);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset")
    public AdminPortfolioDto resetPortfolio(@PathVariable Long id) {
        return portfolioService.resetPortfolio(id);
    }
}
