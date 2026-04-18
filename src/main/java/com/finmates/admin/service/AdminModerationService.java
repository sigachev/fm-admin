package com.finmates.admin.service;

import com.finmates.admin.client.FmMainClient;
import com.finmates.admin.client.FmSocialClient;
import com.finmates.admin.dto.*;
import com.finmates.admin.entity.main.AuditAction;
import com.finmates.admin.entity.main.AuditTargetType;
import com.finmates.admin.repository.main.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates moderation actions across fm-social and finmates-main.
 * Each method:
 *   1. Calls the appropriate internal API (fm-social or fm-main)
 *   2. Writes an audit_log entry (same @Transactional boundary for the audit write)
 *
 * HTTP calls to external services are NOT inside the DB transaction — this is
 * unavoidable with cross-service calls. The audit write is always synchronous
 * in the same request. If the HTTP call fails, an exception is thrown before
 * the audit write, keeping them consistent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final FmSocialClient socialClient;
    private final FmMainClient mainClient;
    private final AuditLogService auditLogService;
    private final AdminUserRepository adminUserRepository;

    // ── Reports ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> listReports(String status, String reason, int page, int size) {
        return socialClient.listReports(status, reason, page, size);
    }

    public ReportDetailResponse getReport(Long id) {
        return socialClient.getReport(id);
    }

    /**
     * Resolve a report with an optional content/user action.
     * Orchestration order:
     *   1. Fetch the report to get target info
     *   2. Execute the content/user action (if not DISMISS)
     *   3. Write audit entry
     *   4. Mark the report as resolved in fm-social
     */
    @Transactional
    public ReportDetailResponse resolveReport(Long reportId,
                                               ResolveReportRequest request,
                                               Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        ReportDetailResponse report = socialClient.getReport(reportId);

        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + reportId);
        }

        String action = request.action() != null ? request.action().toUpperCase() : "DISMISS";
        String auditTargetDesc = report.getTargetType() + " #" + report.getTargetId();

        switch (action) {
            case "REMOVE_POST" -> {
                socialClient.removePost(report.getTargetId(), adminDbId, request.notes());
                auditLogService.record(AuditAction.POST_REMOVED, AuditTargetType.POST,
                        report.getTargetId(), auditTargetDesc, request.notes(), actorJwt);
            }
            case "REMOVE_COMMENT" -> {
                socialClient.removeComment(report.getTargetId(), adminDbId, request.notes());
                auditLogService.record(AuditAction.COMMENT_REMOVED, AuditTargetType.COMMENT,
                        report.getTargetId(), auditTargetDesc, request.notes(), actorJwt);
            }
            case "BAN_USER" -> {
                if (!"USER".equalsIgnoreCase(report.getTargetType())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "BAN_USER action requires targetType=USER on the report");
                }
                mainClient.banUser(report.getTargetId(),
                        request.banType() != null ? request.banType() : "SUSPENSION",
                        request.notes(),
                        request.banDurationDays(),
                        adminDbId);
                auditLogService.record(AuditAction.USER_BANNED, AuditTargetType.USER,
                        report.getTargetId(), "User #" + report.getTargetId(),
                        request.notes(), actorJwt);
            }
            case "DISMISS" -> {
                // No content action; audit below
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown action: " + action);
        }

        // Audit the report resolution itself
        AuditAction auditAction = "DISMISS".equals(action)
                ? AuditAction.REPORT_DISMISSED
                : AuditAction.REPORT_RESOLVED;
        auditLogService.record(auditAction, AuditTargetType.REPORT,
                reportId, "Report #" + reportId, request.notes(), actorJwt);

        // Update report status in fm-social
        String socialStatus = "DISMISS".equals(action) ? "DISMISSED" : "REVIEWED";
        return socialClient.resolveReport(reportId, socialStatus, action, request.notes(), adminDbId);
    }

    @Transactional
    public ReportDetailResponse dismissReport(Long reportId, DismissReportRequest request, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);

        ReportDetailResponse report = socialClient.getReport(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + reportId);
        }

        auditLogService.record(AuditAction.REPORT_DISMISSED, AuditTargetType.REPORT,
                reportId, "Report #" + reportId, request.notes(), actorJwt);

        return socialClient.resolveReport(reportId, "DISMISSED", "DISMISS",
                request.notes(), adminDbId);
    }

    // ── Content preview ───────────────────────────────────────────────────────

    public PostContentResponse getPostPreview(Long postId) {
        return socialClient.getPostPreview(postId);
    }

    public CommentContentResponse getCommentPreview(Long commentId) {
        return socialClient.getCommentPreview(commentId);
    }

    // ── Content (direct, not via report) ─────────────────────────────────────

    @Transactional
    public PostContentResponse removePost(Long postId, RemoveContentRequest request, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        PostContentResponse result = socialClient.removePost(postId, adminDbId, request.reason());
        auditLogService.record(AuditAction.POST_REMOVED, AuditTargetType.POST,
                postId, "Post #" + postId, request.reason(), actorJwt);
        return result;
    }

    @Transactional
    public PostContentResponse restorePost(Long postId, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        PostContentResponse result = socialClient.restorePost(postId, adminDbId);
        auditLogService.record(AuditAction.POST_RESTORED, AuditTargetType.POST,
                postId, "Post #" + postId, null, actorJwt);
        return result;
    }

    @Transactional
    public CommentContentResponse removeComment(Long commentId, RemoveContentRequest request, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        CommentContentResponse result = socialClient.removeComment(commentId, adminDbId, request.reason());
        auditLogService.record(AuditAction.COMMENT_REMOVED, AuditTargetType.COMMENT,
                commentId, "Comment #" + commentId, request.reason(), actorJwt);
        return result;
    }

    @Transactional
    public CommentContentResponse restoreComment(Long commentId, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        CommentContentResponse result = socialClient.restoreComment(commentId, adminDbId);
        auditLogService.record(AuditAction.COMMENT_RESTORED, AuditTargetType.COMMENT,
                commentId, "Comment #" + commentId, null, actorJwt);
        return result;
    }

    // ── User bans (via fm-main) ───────────────────────────────────────────────

    @Transactional
    public UserBanResponse banUser(Long userId, BanUserRequest request, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        UserBanResponse result = mainClient.banUser(userId,
                request.banType(), request.reason(), request.durationDays(), adminDbId);
        auditLogService.record(AuditAction.USER_BANNED, AuditTargetType.USER,
                userId, "User #" + userId, request.reason(), actorJwt);
        return result;
    }

    @Transactional
    public UserBanResponse unbanUser(Long userId, Jwt actorJwt) {
        Long adminDbId = resolveActorDbId(actorJwt);
        UserBanResponse result = mainClient.unbanUser(userId, adminDbId);
        auditLogService.record(AuditAction.USER_UNBANNED, AuditTargetType.USER,
                userId, "User #" + userId, null, actorJwt);
        return result;
    }

    public List<UserBanResponse> getBanHistory(Long userId) {
        return mainClient.getBanHistory(userId);
    }

    public BanStatusResponse getBanStatus(Long userId) {
        return mainClient.getBanStatus(userId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Look up admin's DB user ID from JWT keycloak subject.
     * Returns null if not found — audit entry will have null actorUserId.
     */
    private Long resolveActorDbId(Jwt jwt) {
        return adminUserRepository.findByKeycloakId(jwt.getSubject())
                .map(u -> u.getId())
                .orElse(null);
    }
}
