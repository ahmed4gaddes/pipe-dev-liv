package com.pipedevliv.audit.repository;

import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.entity.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AuditLogRepository repository;

    @Test
    void search_noFilters_returnsAll() {
        entityManager.persistAndFlush(log(AuditEventType.TICKET_CREATED, "TICKET", 1L, "dev-1"));
        entityManager.persistAndFlush(log(AuditEventType.PIPELINE_STARTED, "PIPELINE_EXECUTION", 2L, "tl-1"));

        Page<AuditLog> result = repository.search(null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void search_byEventType_filters() {
        entityManager.persistAndFlush(log(AuditEventType.TICKET_CREATED, "TICKET", 1L, "dev-1"));
        entityManager.persistAndFlush(log(AuditEventType.TICKET_APPROVED, "TICKET", 1L, "tl-1"));

        Page<AuditLog> result = repository.search(AuditEventType.TICKET_APPROVED, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(AuditEventType.TICKET_APPROVED);
    }

    @Test
    void search_byEntityTypeAndEntityId_filters() {
        entityManager.persistAndFlush(log(AuditEventType.TICKET_CREATED, "TICKET", 1L, "dev-1"));
        entityManager.persistAndFlush(log(AuditEventType.TICKET_CREATED, "TICKET", 2L, "dev-2"));
        entityManager.persistAndFlush(log(AuditEventType.PIPELINE_STARTED, "PIPELINE_EXECUTION", 1L, "tl-1"));

        Page<AuditLog> result = repository.search(null, "TICKET", 1L, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getActorUserId()).isEqualTo("dev-1");
    }

    @Test
    void search_byActorUserId_filters() {
        entityManager.persistAndFlush(log(AuditEventType.TICKET_CREATED, "TICKET", 1L, "dev-1"));
        entityManager.persistAndFlush(log(AuditEventType.TICKET_STATUS_CHANGED, "TICKET", 1L, "tl-1"));

        Page<AuditLog> result = repository.search(null, null, null, "tl-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(AuditEventType.TICKET_STATUS_CHANGED);
    }

    private AuditLog log(AuditEventType eventType, String entityType, Long entityId, String actorUserId) {
        return AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorUserId)
                .description("desc")
                .details("{}")
                .build();
    }
}
