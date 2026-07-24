package com.pipedevliv.pipeline.repository;

import com.pipedevliv.pipeline.entity.PipelineExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// findAll(Pageable) est déjà hérité de JpaRepository/PagingAndSortingRepository, pas besoin
// de le redéclarer ici pour la liste paginée du controller.
public interface PipelineExecutionRepository extends JpaRepository<PipelineExecution, Long> {

    Optional<PipelineExecution> findByGithubRunId(Long githubRunId);

    List<PipelineExecution> findByTicketIdOrderByStartedAtDesc(Long ticketId);
}
