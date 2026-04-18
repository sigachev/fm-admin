package com.finmates.admin.controller;

import com.finmates.admin.dto.*;
import com.finmates.admin.service.AdminModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminModerationService moderationService;

    /**
     * List reports from fm-social.
     * Proxies to fm-social GET /api/internal/reports with X-Internal-Secret header.
     * Returns the raw page JSON from fm-social for pass-through to the admin UI.
     */
    @GetMapping
    public Map<String, Object> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return moderationService.listReports(status, reason, page, size);
    }

    @GetMapping("/{id}")
    public ReportDetailResponse getReport(@PathVariable Long id) {
        return moderationService.getReport(id);
    }

    /**
     * Resolve a report with an optional content or user action.
     * action values: REMOVE_POST | REMOVE_COMMENT | BAN_USER | DISMISS
     */
    @PostMapping("/{id}/resolve")
    @ResponseStatus(HttpStatus.OK)
    public ReportDetailResponse resolveReport(
            @PathVariable Long id,
            @RequestBody ResolveReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.resolveReport(id, request, jwt);
    }

    /**
     * Shortcut to dismiss a report without content action.
     */
    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.OK)
    public ReportDetailResponse dismissReport(
            @PathVariable Long id,
            @RequestBody DismissReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.dismissReport(id, request, jwt);
    }
}
