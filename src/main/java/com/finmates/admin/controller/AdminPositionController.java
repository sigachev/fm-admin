package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminPositionDto;
import com.finmates.admin.service.AdminPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/positions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPositionController {

    private final AdminPositionService positionService;

    @GetMapping
    public Page<AdminPositionDto> listPositions(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        return positionService.listPositions(symbol, userId, pageable);
    }
}
