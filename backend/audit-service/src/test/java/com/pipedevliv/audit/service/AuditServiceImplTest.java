package com.pipedevliv.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditLogRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void handleEvent_userSynced_recordsUserEntry() {
        UserSyncedPayload payload = UserSyncedPayload.builder().id(7L).keycloakId("kc-1").email("a@x.com").build();

        service.handleEvent(RabbitMQConstants.USER_SYNCED, payload);

        AuditLog saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.USER_SYNCED);
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo(7L);
        assertThat(saved.getActorUserId()).isEqualTo("kc-1");
        assertThat(saved.getDetails()).contains("kc-1");
    }

    @Test
    void handleEvent_ticketCreated_recordsTicketEntry() {
        TicketCreatedPayload payload = TicketCreatedPayload.builder().id(5L).title("Nouveau ticket").createdByUserId("dev-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_CREATED, payload);

        AuditLog saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.TICKET_CREATED);
        assertThat(saved.getEntityType()).isEqualTo("TICKET");
        assertThat(saved.getEntityId()).isEqualTo(5L);
        assertThat(saved.getActorUserId()).isEqualTo("dev-1");
        assertThat(saved.getDescription()).contains("Nouveau ticket");
        assertThat(saved.getDetails()).contains("Nouveau ticket");
    }

    @Test
    void handleEvent_ticketStatusChanged_recordsWithChangedByAsActor() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(5L).title("T").oldStatus("SUBMITTED").newStatus("APPROVED").changedByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_STATUS_CHANGED, payload);

        AuditLog saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.TICKET_STATUS_CHANGED);
        assertThat(saved.getEntityId()).isEqualTo(5L);
        assertThat(saved.getActorUserId()).isEqualTo("tl-1");
        assertThat(saved.getDescription()).contains("SUBMITTED").contains("APPROVED");
    }

    @Test
    void handleEvent_ticketApproved_recordsEntry() {
        TicketEventPayload payload = TicketEventPayload.builder().ticketId(5L).changedByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_APPROVED, payload);

        AuditLog saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.TICKET_APPROVED);
        assertThat(saved.getActorUserId()).isEqualTo("tl-1");
    }

    @Test
    void handleEvent_pipelineStarted_recordsPipelineExecutionEntry() {
        PipelineEventPayload payload = PipelineEventPayload.builder()
                .executionId(1L).ticketId(5L).environment("DEV").status("QUEUED").triggeredByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.PIPELINE_STARTED, payload);

        AuditLog saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.PIPELINE_STARTED);
        assertThat(saved.getEntityType()).isEqualTo("PIPELINE_EXECUTION");
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getActorUserId()).isEqualTo("tl-1");
        assertThat(saved.getDescription()).contains("DEV").contains("QUEUED");
    }

    @Test
    void handleEvent_pipelineCompleted_recordsEntry() {
        PipelineEventPayload payload = PipelineEventPayload.builder()
                .executionId(1L).environment("DEV").status("SUCCESS").triggeredByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.PIPELINE_COMPLETED, payload);

        assertThat(captureSaved().getEventType()).isEqualTo(AuditEventType.PIPELINE_COMPLETED);
    }

    @Test
    void handleEvent_pipelineFailed_recordsEntry() {
        PipelineEventPayload payload = PipelineEventPayload.builder()
                .executionId(1L).environment("PROD").status("FAILED").triggeredByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.PIPELINE_FAILED, payload);

        assertThat(captureSaved().getEventType()).isEqualTo(AuditEventType.PIPELINE_FAILED);
    }

    @Test
    void handleEvent_unknownRoutingKey_doesNotSave() {
        service.handleEvent("some.other.key", new Object());

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void listLogs_delegatesToRepositorySearch() {
        when(auditLogRepository.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        AuditLogFilterDTO filter = AuditLogFilterDTO.builder().entityType("TICKET").build();
        PageResponse<?> result = service.listLogs(filter, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(auditLogRepository).search(null, "TICKET", null, null, PageRequest.of(0, 20));
    }

    @Test
    void getLogById_found_returnsDTO() {
        AuditLog entry = AuditLog.builder().id(1L).eventType(AuditEventType.TICKET_CREATED)
                .entityType("TICKET").entityId(5L).actorUserId("dev-1").description("d").details("{}").build();
        when(auditLogRepository.findById(1L)).thenReturn(Optional.of(entry));

        assertThat(service.getLogById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getLogById_notFound_throws() {
        when(auditLogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLogById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
