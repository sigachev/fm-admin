package com.finmates.admin.controller;

import com.finmates.admin.dto.AuditLogResponse;
import com.finmates.admin.dto.PageResponse;
import com.finmates.admin.entity.main.AuditAction;
import com.finmates.admin.entity.main.AuditTargetType;
import com.finmates.admin.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogService auditLogService;

    /**
     * Search audit log with optional filters. Newest first.
     *
     * @param action     AuditAction enum name (POST_REMOVED, USER_BANNED, etc.)
     * @param targetType AuditTargetType enum name (POST, COMMENT, USER, REPORT, SYSTEM)
     * @param targetId   DB ID of the target
     * @param actorUserId DB ID of the admin who performed the action
     * @param startDate  ISO-8601 start date (inclusive)
     * @param endDate    ISO-8601 end date (inclusive)
     */
    @GetMapping
    public PageResponse<AuditLogResponse> listAuditEntries(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        AuditAction actionEnum = parseEnum(AuditAction.class, action);
        AuditTargetType targetTypeEnum = parseEnum(AuditTargetType.class, targetType);
        OffsetDateTime start = startDate != null ? OffsetDateTime.parse(startDate) : null;
        OffsetDateTime end = endDate != null ? OffsetDateTime.parse(endDate) : null;

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLogResponse> result = auditLogService
                .search(actionEnum, targetTypeEnum, targetId, actorUserId, start, end, pageable)
                .map(AuditLogResponse::from);

        return PageResponse.from(result);
    }

    /**
     * All audit entries where userId is either the actor OR the subject of the action.
     */
    @GetMapping("/user/{userId}")
    public PageResponse<AuditLogResponse> getUserAuditHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLogResponse> result = auditLogService
                .getForUser(userId, pageable)
                .map(AuditLogResponse::from);

        return PageResponse.from(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // Unknown value → no filter applied
        }
    }
}
