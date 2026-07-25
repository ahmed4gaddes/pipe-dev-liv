package com.pipedevliv.audit.dto;

import com.pipedevliv.audit.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogFilterDTO {
    private AuditEventType eventType;
    private String entityType;
    private Long entityId;
    private String actorUserId;
}
