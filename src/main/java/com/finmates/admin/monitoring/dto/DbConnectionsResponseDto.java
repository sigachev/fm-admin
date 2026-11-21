package com.finmates.admin.monitoring.dto;

import java.time.Instant;
import java.util.List;

/**
 * Envelope for {@code GET /api/admin/services/db-connections}.
 */
public record DbConnectionsResponseDto(
        DbConnectionsSummaryDto summary,
        List<DbConnectionRowDto> rows,
        Instant lastCheckedAt
) {
}
