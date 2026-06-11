package com.finmates.admin.controller;

import com.finmates.admin.dto.services.DbConnectionsResponseDto;
import com.finmates.admin.dto.services.ServiceStatusDto;
import com.finmates.admin.service.ServicesStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only services dashboard endpoints.
 *
 * <p>Both responses are served from a ~10 s in-memory cache; pg_stat_activity is
 * queried at most once per refresh and ~9 health probes run sequentially with
 * the short-timeout {@code healthRestTemplate}.
 *
 * <p>No mutating endpoints live here by design — the dashboard is observational
 * only (no restart/scale/deploy buttons), per the locked Phase 1 scope.
 */
@RestController
@RequestMapping("/api/admin/services")
@PreAuthorize("hasRole('ADMIN')")
public class AdminServicesController {

    private final ServicesStatusService servicesStatusService;

    public AdminServicesController(ServicesStatusService servicesStatusService) {
        this.servicesStatusService = servicesStatusService;
    }

    @GetMapping("/status")
    public List<ServiceStatusDto> getServicesStatus() {
        return servicesStatusService.getServicesStatus();
    }

    @GetMapping("/db-connections")
    public DbConnectionsResponseDto getDbConnections() {
        return servicesStatusService.getDbConnections();
    }
}
