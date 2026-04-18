package com.finmates.admin.dto;

/**
 * DTO matching the JSON shape returned by finmates-main
 * GET /api/internal/users/{id}/ban-status.
 */
public record BanStatusResponse(boolean banned, String banType, String expiresAt) {}
