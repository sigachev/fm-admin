package com.finmates.admin.service;

import com.finmates.admin.entity.main.AuditAction;
import com.finmates.admin.entity.main.AuditLog;
import com.finmates.admin.entity.main.AuditTargetType;
import com.finmates.admin.repository.main.AdminUserRepository;
import com.finmates.admin.repository.main.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and queries the audit_log table (main DB).
 * Uses the @Primary mainTransactionManager — no explicit TM qualifier needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AdminUserRepository adminUserRepository;

    /**
     * Record an audit entry.
     * Extracts actor info from the JWT: keycloak subject → DB user ID, preferred_username → actor name.
     * If the admin user is not found in the main DB (edge case), actorUserId is stored as null.
     */
    @Transactional
    public AuditLog record(AuditAction action,
                           AuditTargetType targetType,
                           Long targetId,
                           String targetDescription,
                           String reason,
                           Jwt actorJwt) {
        String keycloakId = actorJwt.getSubject();
        String actorUsername = actorJwt.getClaimAsString("preferred_username");

        Long actorUserId = adminUserRepository.findByKeycloakId(keycloakId)
                .map(u -> u.getId())
                .orElse(null);

        if (actorUserId == null) {
            log.warn("AuditLog: admin user not found in DB for keycloakId={}, username={}",
                    keycloakId, actorUsername);
        }

        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTargetDescription(targetDescription);
        entry.setReason(reason);
        entry.setActorUserId(actorUserId);
        entry.setActorUsername(actorUsername != null ? actorUsername : keycloakId);

        AuditLog saved = auditLogRepository.save(entry);
        log.info("Audit: action={} targetType={} targetId={} actor={}",
                action, targetType, targetId, actorUsername);
        return saved;
    }

    /**
     * Paginated search with optional filters.
     * All parameters are optional — null means "no filter on this field".
     */
    public Page<AuditLog> search(AuditAction action,
                                 AuditTargetType targetType,
                                 Long targetId,
                                 Long actorUserId,
                                 OffsetDateTime startDate,
                                 OffsetDateTime endDate,
                                 Pageable pageable) {
        Specification<AuditLog> spec = buildSpec(action, targetType, targetId,
                actorUserId, startDate, endDate);
        return auditLogRepository.findAll(spec, pageable);
    }

    /**
     * All audit entries where userId is either the actor OR the subject of the action.
     */
    public Page<AuditLog> getForUser(Long userId, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            Predicate asActor = cb.equal(root.get("actorUserId"), userId);
            Predicate asTarget = cb.and(
                    cb.equal(root.get("targetType"), AuditTargetType.USER),
                    cb.equal(root.get("targetId"), userId)
            );
            return cb.or(asActor, asTarget);
        };
        return auditLogRepository.findAll(spec, pageable);
    }

    // ── Specification builder ───────────────────────────────────────────────────

    private Specification<AuditLog> buildSpec(AuditAction action,
                                               AuditTargetType targetType,
                                               Long targetId,
                                               Long actorUserId,
                                               OffsetDateTime startDate,
                                               OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
