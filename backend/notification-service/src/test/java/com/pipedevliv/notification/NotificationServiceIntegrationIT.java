package com.pipedevliv.notification;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.notification.dto.TicketEventPayload;
import com.pipedevliv.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * Publie un vrai message RabbitMQ (routing key ticket.status-changed) via le RabbitTemplate
 * configuré de notification-service — donc avec le même DefaultJackson2JavaTypeMapper que la
 * prod, qui réécrit __TypeId__ vers le FQCN "producteur" (com.pipedevliv.ticket.messaging.TicketEvent)
 * grâce au mapping bidirectionnel de setIdClassMapping (voir RabbitMQConfig, Phase 6) — puis
 * vérifie que le @RabbitListener réel consomme ce message et persiste une vraie ligne Notification
 * en base, de bout en bout, sans rien mocker.
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class NotificationServiceIntegrationIT {

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
    private NotificationRepository notificationRepository;

    @Test
    void ticketStatusChangedEvent_isConsumedAndPersistedAsRealNotification() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(77L)
                .title("IT ticket")
                .oldStatus("SUBMITTED")
                .newStatus("APPROVED")
                .changedByUserId("approver-1")
                .createdByUserId("owner-1")
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.TICKET_STATUS_CHANGED, payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Page<com.pipedevliv.notification.entity.Notification> page =
                    notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc("owner-1", PageRequest.of(0, 10));
            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent().get(0).getMessage()).contains("77");
        });
    }
}
