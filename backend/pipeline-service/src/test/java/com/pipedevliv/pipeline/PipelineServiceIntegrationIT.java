package com.pipedevliv.pipeline;

import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import com.pipedevliv.pipeline.repository.PipelineExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance PipelineExecution contre une vraie Postgres. Ne touche délibérément pas
 * GitHubActionsClient : appeler la vraie API GitHub depuis un test automatisé n'a rien à faire ici
 * (réseau externe, jeton réel requis, risque de déclencher un vrai run) — seule la couche
 * persistance/repository est vérifiée en conditions réelles.
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class PipelineServiceIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PipelineExecutionRepository repository;

    @Test
    void persistAndQuery_roundTripsThroughRealPostgres() {
        PipelineExecution execution = repository.save(PipelineExecution.builder()
                .ticketId(42L)
                .environment("dev")
                .status(PipelineStatus.QUEUED)
                .triggeredByUserId("it-user-1")
                .gitBranch("main")
                .gitCommitSha("abc123")
                .build());

        assertThat(execution.getId()).isNotNull();
        assertThat(execution.getStartedAt()).isNotNull();

        Optional<PipelineExecution> byRunId = repository.findByGithubRunId(999L);
        assertThat(byRunId).isEmpty();

        execution.setGithubRunId(999L);
        repository.save(execution);
        assertThat(repository.findByGithubRunId(999L)).isPresent();

        List<PipelineExecution> byTicket = repository.findByTicketIdOrderByStartedAtDesc(42L);
        assertThat(byTicket).hasSize(1);
        assertThat(byTicket.get(0).getStatus()).isEqualTo(PipelineStatus.QUEUED);
    }
}
