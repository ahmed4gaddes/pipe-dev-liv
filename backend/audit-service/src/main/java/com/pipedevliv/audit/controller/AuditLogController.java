package com.pipedevliv.audit.controller;

import com.pipedevliv.audit.dto.AuditLogDTO;
import com.pipedevliv.audit.dto.AuditLogFilterDTO;
import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.service.AuditService;
import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AuditLogDTO>> list(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String actorUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        AuditLogFilterDTO filter = AuditLogFilterDTO.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorUserId)
                .build();
        return ApiResponse.success(auditService.listLogs(filter, pageable), "Journal d'audit récupéré");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AuditLogDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(auditService.getLogById(id), "Entrée récupérée");
    }
}
