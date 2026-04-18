package com.finmates.admin.dto;

/**
 * Admin request body for POST /api/admin/users/{id}/ban.
 * banType: SUSPENSION (time-limited) or PERMANENT.
 * durationDays: required for SUSPENSION; ignored for PERMANENT.
 */
public record BanUserRequest(
        String banType,
        Integer durationDays,
        String reason
) {}
