package com.pipedevliv.ticket;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le test le plus précieux de cette phase : crée un ticket puis le soumet à travers TicketService
 * (persistance + TicketStateMachine réels), et vérifie que les événements ticket.created /
 * ticket.status-changed sont réellement publiés sur l'exchange pipe-dev-liv.events — Postgres et
 * RabbitMQ réels (Testcontainers), pas H2/mocks. Une file de test anonyme, liée avec le pattern
 * "ticket.*", capture les messages sans dépendre des files de consommateurs réels.
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class TicketServiceIntegrationIT {

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
    private TicketService ticketService;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TopicExchange eventsExchange;

    private String testQueueName;

    @BeforeEach
    void bindAnonymousTestQueue() {
        // amqpAdmin.declareQueue() sans argument déclare une file exclusive ET auto-delete : le
        // consommateur temporaire ouvert par le premier rabbitTemplate.receive() se désabonne une
        // fois le message reçu, ce qui supprime immédiatement la file auto-delete — le second
        // receive() de ce test échoue alors avec NOT_FOUND. On garde exclusive (nettoyée à la
        // fermeture de la connexion Testcontainers) mais pas auto-delete.
        Queue queue = new Queue(java.util.UUID.randomUUID().toString(), false, true, false);
        amqpAdmin.declareQueue(queue);
        testQueueName = queue.getName();
        Binding binding = BindingBuilder.bind(queue).to(eventsExchange).with("ticket.*");
        amqpAdmin.declareBinding(binding);
    }

    @Test
    void createThenSubmit_persistsRealRowsAndPublishesRealEvents() {
        TicketCreateDTO createDto = TicketCreateDTO.builder()
                .title("IT — déploiement de test")
                .description("Créé par TicketServiceIntegrationIT")
                .priority(TicketPriority.MEDIUM)
                .targetEnvironment("dev")
                .gitBranch("main")
                .gitCommitSha("abc123")
                .build();

        TicketResponseDTO created = ticketService.createTicket(createDto, "it-user-1");
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(TicketStatus.DRAFT);

        Message createdEvent = rabbitTemplate.receive(testQueueName, 5000);
        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(RabbitMQConstants.TICKET_CREATED);

        TicketStatusChangeDTO submitDto = TicketStatusChangeDTO.builder()
                .newStatus(TicketStatus.SUBMITTED)
                .comment("Soumission IT")
                .build();
        TicketResponseDTO submitted = ticketService.changeStatus(created.getId(), submitDto, "it-user-1");
        assertThat(submitted.getStatus()).isEqualTo(TicketStatus.SUBMITTED);

        Message statusEvent = rabbitTemplate.receive(testQueueName, 5000);
        assertThat(statusEvent).isNotNull();
        assertThat(statusEvent.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(RabbitMQConstants.TICKET_STATUS_CHANGED);

        TicketResponseDTO refetched = ticketService.getTicketById(created.getId());
        assertThat(refetched.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
    }
}
