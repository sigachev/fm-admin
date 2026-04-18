package com.finmates.admin.dto;

import com.finmates.admin.entity.main.AuditAction;
import com.finmates.admin.entity.main.AuditLog;
import com.finmates.admin.entity.main.AuditTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long actorUserId;
    private String actorUsername;
    private AuditAction action;
    private AuditTargetType targetType;
    private Long targetId;
    private String targetDescription;
    private String reason;
    private OffsetDateTime createdAt;

    public static AuditLogResponse from(AuditLog log) {
        AuditLogResponse r = new AuditLogResponse();
        r.setId(log.getId());
        r.setActorUserId(log.getActorUserId());
        r.setActorUsername(log.getActorUsername());
        r.setAction(log.getAction());
        r.setTargetType(log.getTargetType());
        r.setTargetId(log.getTargetId());
        r.setTargetDescription(log.getTargetDescription());
        r.setReason(log.getReason());
        r.setCreatedAt(log.getCreatedAt());
        return r;
    }
}
