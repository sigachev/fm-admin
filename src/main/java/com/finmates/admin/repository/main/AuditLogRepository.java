package com.finmates.admin.repository.main;

import com.finmates.admin.entity.main.AuditLog;
import com.finmates.admin.entity.main.AuditTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId);

    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            AuditTargetType targetType, Long targetId);
}
