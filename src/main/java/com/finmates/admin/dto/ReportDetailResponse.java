package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO matching the JSON shape returned by fm-social
 * GET /api/internal/reports and PUT /api/internal/reports/{id}/resolve.
 * Fields are kept as Strings/Long to avoid enum coupling across services.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReportDetailResponse {
    private Long id;
    private Long reporterId;
    private String reporterUsername;
    private String targetType;   // POST | COMMENT | USER
    private Long targetId;
    private String reason;       // SPAM | HARASSMENT | etc.
    private String details;
    private String status;       // PENDING | REVIEWED | DISMISSED
    private String resolutionAction;
    private Long resolvedBy;
    private String resolvedAt;
    private String createdAt;
}
