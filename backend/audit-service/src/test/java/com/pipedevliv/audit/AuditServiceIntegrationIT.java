package com.pipedevliv.audit;

import com.pipedevliv.audit.dto.TicketEventPayload;
import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.repository.AuditLogRepository;
import com.pipedevliv.common.event.RabbitMQConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Même principe que NotificationServiceIntegrationIT (Phase 10) : publie un vrai message
 * ticket.status-changed via le RabbitTemplate configuré d'audit-service, et vérifie que le
 * @RabbitListener réel persiste une vraie ligne AuditLog — de bout en bout, real Postgres +
 * real RabbitMQ, aucun mock. Contrairement à notification-service, audit-service journalise
 * même les événements sans "destinataire" évident (voir explication_phase_7.md) : ce test
 * confirme qu'aucune ligne n'est silencieusement filtrée.
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class AuditServiceIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void ticketStatusChangedEvent_isConsumedAndPersistedAsRealAuditLog() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(88L)
                .title("IT ticket")
                .oldStatus("SUBMITTED")
                .newStatus("APPROVED")
                .changedByUserId("approver-1")
                .createdByUserId("owner-1")
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.TICKET_STATUS_CHANGED, payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var matches = auditLogRepository.findAll().stream()
                    .filter(a -> a.getEventType() == AuditEventType.TICKET_STATUS_CHANGED)
                    .filter(a -> Long.valueOf(88L).equals(a.getEntityId()))
                    .toList();
            assertThat(matches).isNotEmpty();
            assertThat(matches.get(0).getActorUserId()).isEqualTo("approver-1");
        });
    }
}
