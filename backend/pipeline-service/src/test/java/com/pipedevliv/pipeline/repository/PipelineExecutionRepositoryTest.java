package com.pipedevliv.pipeline.repository;

import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PipelineExecutionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PipelineExecutionRepository repository;

    @Test
    void findByGithubRunId_found() {
        entityManager.persistAndFlush(execution(1L, 999L));

        Optional<PipelineExecution> result = repository.findByGithubRunId(999L);

        assertThat(result).isPresent();
        assertThat(result.get().getTicketId()).isEqualTo(1L);
    }

    @Test
    void findByGithubRunId_notFound_empty() {
        assertThat(repository.findByGithubRunId(12345L)).isEmpty();
    }

    @Test
    void findByTicketIdOrderByStartedAtDesc_scopesToTicket() {
        entityManager.persistAndFlush(execution(1L, 100L));
        entityManager.persistAndFlush(execution(1L, 101L));
        entityManager.persistAndFlush(execution(2L, 200L));

        List<PipelineExecution> result = repository.findByTicketIdOrderByStartedAtDesc(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PipelineExecution::getGithubRunId).containsExactlyInAnyOrder(100L, 101L);
    }

    private PipelineExecution execution(Long ticketId, Long githubRunId) {
        return PipelineExecution.builder()
                .ticketId(ticketId)
                .githubRunId(githubRunId)
                .environment("DEV")
                .status(PipelineStatus.QUEUED)
                .triggeredByUserId("tl-1")
                .gitBranch("main")
                .build();
    }
}
