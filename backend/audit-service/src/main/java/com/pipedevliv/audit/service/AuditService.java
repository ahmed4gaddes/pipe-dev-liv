package com.pipedevliv.audit.service;

import com.pipedevliv.audit.dto.AuditLogDTO;
import com.pipedevliv.audit.dto.AuditLogFilterDTO;
import com.pipedevliv.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AuditService {

    void handleEvent(String routingKey, Object payload);

    PageResponse<AuditLogDTO> listLogs(AuditLogFilterDTO filter, Pageable pageable);

    AuditLogDTO getLogById(Long id);
}
