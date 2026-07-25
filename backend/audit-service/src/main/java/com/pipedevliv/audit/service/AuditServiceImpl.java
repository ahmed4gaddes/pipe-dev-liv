package com.pipedevliv.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipedevliv.audit.dto.AuditLogDTO;
import com.pipedevliv.audit.dto.AuditLogFilterDTO;
import com.pipedevliv.audit.dto.PipelineEventPayload;
import com.pipedevliv.audit.dto.TicketCreatedPayload;
import com.pipedevliv.audit.dto.TicketEventPayload;
import com.pipedevliv.audit.dto.UserSyncedPayload;
import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.entity.AuditLog;
import com.pipedevliv.audit.repository.AuditLogRepository;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void handleEvent(String routingKey, Object payload) {
        switch (routingKey) {
            case RabbitMQConstants.USER_SYNCED -> handleUserSynced((UserSyncedPayload) payload);
            case RabbitMQConstants.TICKET_CREATED -> handleTicketCreated((TicketCreatedPayload) payload);
            case RabbitMQConstants.TICKET_STATUS_CHANGED -> handleTicketStatusChanged((TicketEventPayload) payload);
            case RabbitMQConstants.TICKET_APPROVED -> handleTicketApproved((TicketEventPayload) payload);
            case RabbitMQConstants.PIPELINE_STARTED ->
                    handlePipelineEvent((PipelineEventPayload) payload, AuditEventType.PIPELINE_STARTED);
            case RabbitMQConstants.PIPELINE_COMPLETED ->
                    handlePipelineEvent((PipelineEventPayload) payload, AuditEventType.PIPELINE_COMPLETED);
            case RabbitMQConstants.PIPELINE_FAILED ->
                    handlePipelineEvent((PipelineEventPayload) payload, AuditEventType.PIPELINE_FAILED);
            default -> log.warn("Routing key non gérée par audit-service : {}", routingKey);
        }
    }

    private void handleUserSynced(UserSyncedPayload payload) {
        save(AuditEventType.USER_SYNCED, "USER", payload.getId(), payload.getKeycloakId(),
                "Profil utilisateur synchronisé", payload);
    }

    private void handleTicketCreated(TicketCreatedPayload payload) {
        save(AuditEventType.TICKET_CREATED, "TICKET", payload.getId(), payload.getCreatedByUserId(),
                "Ticket créé : " + payload.getTitle(), payload);
    }

    private void handleTicketStatusChanged(TicketEventPayload payload) {
        String description = "Statut modifié : %s → %s".formatted(payload.getOldStatus(), payload.getNewStatus());
        save(AuditEventType.TICKET_STATUS_CHANGED, "TICKET", payload.getTicketId(), payload.getChangedByUserId(),
                description, payload);
    }

    private void handleTicketApproved(TicketEventPayload payload) {
        save(AuditEventType.TICKET_APPROVED, "TICKET", payload.getTicketId(), payload.getChangedByUserId(),
                "Ticket approuvé", payload);
    }

    private void handlePipelineEvent(PipelineEventPayload payload, AuditEventType type) {
        String description = "Pipeline %s : %s".formatted(payload.getEnvironment(), payload.getStatus());
        save(type, "PIPELINE_EXECUTION", payload.getExecutionId(), payload.getTriggeredByUserId(), description, payload);
    }

    private void save(AuditEventType eventType, String entityType, Long entityId, String actorUserId,
                       String description, Object payload) {
        auditLogRepository.save(AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorUserId)
                .description(description)
                .details(toJson(payload))
                .build());
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Impossible de sérialiser le payload pour l'audit : {}", e.getMessage());
            return payload.toString();
        }
    }

    @Override
    public PageResponse<AuditLogDTO> listLogs(AuditLogFilterDTO filter, Pageable pageable) {
        var page = auditLogRepository.search(
                filter.getEventType(), filter.getEntityType(), filter.getEntityId(), filter.getActorUserId(), pageable);
        return PageResponse.from(page.map(this::toDTO));
    }

    @Override
    public AuditLogDTO getLogById(Long id) {
        return auditLogRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", id));
    }

    private AuditLogDTO toDTO(AuditLog auditLog) {
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .eventType(auditLog.getEventType())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .actorUserId(auditLog.getActorUserId())
                .description(auditLog.getDescription())
                .details(auditLog.getDetails())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
