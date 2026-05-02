package com.finmates.admin.entity.main;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_audit_log_actor", columnList = "actor_user_id"),
        @Index(name = "idx_audit_log_target", columnList = "target_type, target_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AuditTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_description", length = 200)
    private String targetDescription;

    @Column(length = 500)
    private String reason;

    // Stored as JSON string; serialised/deserialised in service layer
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
