package com.pipedevliv.pipeline.repository;

import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// findAll(Pageable) est déjà hérité de JpaRepository/PagingAndSortingRepository, pas besoin
// de le redéclarer ici pour la liste paginée du controller.
public interface PipelineExecutionRepository extends JpaRepository<PipelineExecution, Long> {

    Optional<PipelineExecution> findByGithubRunId(Long githubRunId);

    List<PipelineExecution> findByTicketIdOrderByStartedAtDesc(Long ticketId);

    // Utilisé par la réconciliation périodique (PipelineServiceImpl#reconcilePendingExecutions) :
    // rattrape les exécutions dont le webhook GitHub aurait été manqué (ngrok/tunnel down,
    // runner self-hosted arrêté, 5xx transitoire juste après un redéploiement...).
    List<PipelineExecution> findByStatusInAndGithubRunIdIsNotNull(List<PipelineStatus> statuses);
}
