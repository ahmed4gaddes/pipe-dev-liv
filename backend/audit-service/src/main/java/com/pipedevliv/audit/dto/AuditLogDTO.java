package com.pipedevliv.audit.dto;

import com.pipedevliv.audit.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private AuditEventType eventType;
    private String entityType;
    private Long entityId;
    private String actorUserId;
    private String description;
    private String details;
    private LocalDateTime createdAt;
}
