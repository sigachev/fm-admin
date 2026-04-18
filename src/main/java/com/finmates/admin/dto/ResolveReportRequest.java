package com.finmates.admin.dto;

/**
 * Admin request body for POST /api/admin/reports/{id}/resolve.
 *
 * action values:
 *   REMOVE_POST    — remove the reported post (targetType must be POST)
 *   REMOVE_COMMENT — remove the reported comment (targetType must be COMMENT)
 *   BAN_USER       — ban the reported user (targetType must be USER)
 *   DISMISS        — dismiss without content action
 *
 * banDurationDays / banType are required only when action=BAN_USER.
 */
public record ResolveReportRequest(
        String action,
        String notes,
        Integer banDurationDays,
        String banType          // SUSPENSION | PERMANENT
) {}
