package com.pipedevliv.audit.repository;

import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE (:eventType IS NULL OR a.eventType = :eventType) "
            + "AND (:entityType IS NULL OR a.entityType = :entityType) "
            + "AND (:entityId IS NULL OR a.entityId = :entityId) "
            + "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
            + "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("eventType") AuditEventType eventType,
                           @Param("entityType") String entityType,
                           @Param("entityId") Long entityId,
                           @Param("actorUserId") String actorUserId,
                           Pageable pageable);
}
