package com.pipedevliv.pipeline.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.pipeline.dto.GitHubJobDTO;
import com.pipedevliv.pipeline.dto.GitHubRunDTO;
import com.pipedevliv.pipeline.dto.GitHubWebhookPayload;
import com.pipedevliv.pipeline.dto.PipelineExecutionDTO;
import com.pipedevliv.pipeline.dto.PipelineStageDTO;
import com.pipedevliv.pipeline.dto.PipelineStatusUpdateDTO;
import com.pipedevliv.pipeline.dto.PipelineTriggerDTO;
import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStage;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import com.pipedevliv.pipeline.feign.TicketServiceClient;
import com.pipedevliv.pipeline.messaging.PipelineEventPublisher;
import com.pipedevliv.pipeline.repository.PipelineExecutionRepository;
import com.pipedevliv.pipeline.repository.PipelineStageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);

    private final PipelineExecutionRepository executionRepository;
    private final PipelineStageRepository stageRepository;
    private final GitHubActionsClient gitHubActionsClient;
    private final PipelineEventPublisher eventPublisher;
    private final TicketServiceClient ticketServiceClient;

    @Override
    @Transactional
    public PipelineExecutionDTO triggerPipeline(PipelineTriggerDTO dto, String triggeredByUserId) {
        String branch = (dto.getGitBranch() == null || dto.getGitBranch().trim().isEmpty()) ? "main" : dto.getGitBranch();

        PipelineExecution execution = PipelineExecution.builder()
                .ticketId(dto.getTicketId())
                .environment(dto.getTargetEnvironment())
                .status(PipelineStatus.QUEUED)
                .triggerType("MANUAL")
                .triggeredByUserId(triggeredByUserId)
                .gitBranch(branch)
                .gitCommitSha(dto.getGitCommitSha())
                .build();
        execution = executionRepository.save(execution);

        gitHubActionsClient.triggerWorkflow(branch, Map.of(
                "environment", dto.getTargetEnvironment() == null ? "" : dto.getTargetEnvironment().toLowerCase(),
                "ticket_id", String.valueOf(dto.getTicketId())));

        // workflow_dispatch ne renvoie pas d'ID (204 No Content) et GitHub met parfois 1-2s à
        // indexer le nouveau run dans l'API de listing : sans ce court délai, findLatestRunId
        // retombe sur le run précédent (déjà terminé) au lieu du nouveau, corrélant l'exécution
        // au mauvais run GitHub Actions.
        waitForGitHubIndexing();

        Optional<Long> runId = gitHubActionsClient.findLatestRunId(branch);
        if (runId.isPresent()) {
            execution.setGithubRunId(runId.get());
            execution = executionRepository.save(execution);
        } else {
            log.warn("Impossible de corréler immédiatement le run GitHub Actions pour le ticket {} (branche {})",
                    dto.getTicketId(), branch);
        }

        eventPublisher.publishStarted(execution);
        return toDTO(execution);
    }

    private void waitForGitHubIndexing() {
        try {
            Thread.sleep(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public PipelineExecutionDTO getExecution(Long id) {
        return toDTO(findExecutionOrThrow(id));
    }

    @Override
    public PageResponse<PipelineExecutionDTO> listExecutions(Pageable pageable) {
        Page<PipelineExecution> page = executionRepository.findAll(pageable);
        return PageResponse.from(page.map(this::toDTO));
    }

    @Override
    public List<PipelineExecutionDTO> listByTicket(Long ticketId) {
        return executionRepository.findByTicketIdOrderByStartedAtDesc(ticketId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<PipelineStageDTO> getStages(Long executionId) {
        findExecutionOrThrow(executionId);
        return stageRepository.findByExecutionIdOrderByStageOrderAsc(executionId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public String getLogsUrl(Long executionId) {
        PipelineExecution execution = findExecutionOrThrow(executionId);
        if (execution.getGithubRunId() == null) {
            throw new BusinessException("Aucun run GitHub Actions associé pour l'instant à cette exécution");
        }
        return gitHubActionsClient.getRun(execution.getGithubRunId()).getHtmlUrl();
    }

    @Override
    @Transactional
    public void deleteExecution(Long id) {
        PipelineExecution execution = findExecutionOrThrow(id);
        if (execution.getStatus() == PipelineStatus.QUEUED || execution.getStatus() == PipelineStatus.RUNNING) {
            throw new BusinessException("Impossible de supprimer une exécution en cours");
        }
        stageRepository.deleteByExecutionId(id);
        executionRepository.delete(execution);
    }

    @Override
    @Transactional
    public void handleWorkflowRunEvent(GitHubWebhookPayload payload) {
        GitHubWebhookPayload.WorkflowRun workflowRun = payload.getWorkflowRun();
        if (workflowRun == null || workflowRun.getId() == null) {
            log.warn("Webhook workflow_run reçu sans workflow_run.id exploitable, ignoré");
            return;
        }

        Optional<PipelineExecution> executionOpt = executionRepository.findByGithubRunId(workflowRun.getId());
        if (executionOpt.isEmpty()) {
            log.info("Aucune PipelineExecution connue pour le run GitHub {} (action={}), ignoré",
                    workflowRun.getId(), payload.getAction());
            return;
        }
        applyRunUpdate(executionOpt.get(), workflowRun.getStatus(), workflowRun.getConclusion());
    }

    // Filet de sécurité : le webhook GitHub est le chemin normal, mais peut être manqué
    // (tunnel ngrok tombé, runner self-hosted arrêté, 5xx transitoire juste après un
    // redéploiement — tous des incidents déjà vécus sur cette stack). Ce job périodique
    // rattrape tout écart en interrogeant directement l'API GitHub pour chaque exécution
    // encore QUEUED/RUNNING, sur DEV/TEST/PROD indifféremment (la logique ne dépend pas de
    // l'environnement). initialDelay laisse le temps au service de finir son démarrage.
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional
    public void reconcilePendingExecutions() {
        List<PipelineExecution> pending = executionRepository.findByStatusInAndGithubRunIdIsNotNull(
                List.of(PipelineStatus.QUEUED, PipelineStatus.RUNNING));

        for (PipelineExecution execution : pending) {
            try {
                GitHubRunDTO run = gitHubActionsClient.getRun(execution.getGithubRunId());
                if (applyRunUpdate(execution, run.getStatus(), run.getConclusion())) {
                    log.info("Réconciliation : exécution {} (run GitHub {}) rattrapée, webhook probablement manqué",
                            execution.getId(), execution.getGithubRunId());
                }
            } catch (Exception ex) {
                log.warn("Réconciliation : échec de la vérification du run GitHub {} pour l'exécution {} : {}",
                        execution.getGithubRunId(), execution.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Applique un statut/conclusion GitHub (venant soit du webhook, soit de la réconciliation
     * périodique) à une exécution. Idempotent : une exécution déjà dans un état terminal n'est
     * jamais re-traitée, donc webhook et réconciliation peuvent se chevaucher sans double
     * notification de ticket-service.
     *
     * Appelée uniquement en interne (webhook ou réconciliation), donc toujours dans la
     * transaction déjà ouverte par l'appelant — pas de @Transactional propre ici,
     * l'auto-invocation via {@code this} contournerait de toute façon le proxy Spring.
     *
     * @return true si l'exécution vient d'être finalisée (utile pour le log de réconciliation)
     */
    private boolean applyRunUpdate(PipelineExecution execution, String status, String conclusion) {
        if (!"completed".equals(status)) {
            if ("in_progress".equals(status) && execution.getStatus() == PipelineStatus.QUEUED) {
                execution.setStatus(PipelineStatus.RUNNING);
                executionRepository.save(execution);
            }
            return false;
        }

        if (execution.getStatus() == PipelineStatus.SUCCESS
                || execution.getStatus() == PipelineStatus.FAILED
                || execution.getStatus() == PipelineStatus.CANCELLED) {
            return false;
        }

        PipelineStatus newStatus = mapConclusion(conclusion);
        execution.setStatus(newStatus);
        execution.setCompletedAt(java.time.LocalDateTime.now());
        execution = executionRepository.save(execution);

        persistStages(execution);

        if (newStatus == PipelineStatus.SUCCESS) {
            eventPublisher.publishCompleted(execution);
            notifyTicketService(execution, "SUCCESS");
        } else {
            // FAILED comme CANCELLED doivent sortir le ticket de DEPLOYING_* : c'est le seul
            // événement qui l'en fait sortir. Ne pas notifier sur CANCELLED (comportement
            // précédent) laissait le ticket bloqué en "Déploiement ... " indéfiniment, sans
            // aucun moyen de le relancer. ticket-service mappe tout statut non-"SUCCESS" vers
            // FAILED, d'où le ticket redevient rejouable (FAILED -> SUBMITTED). Le libellé exact
            // ("CANCELLED"/"FAILED") est conservé dans l'historique du ticket.
            eventPublisher.publishFailed(execution);
            notifyTicketService(execution, newStatus.name());
        }
        return true;
    }

    private void notifyTicketService(PipelineExecution execution, String status) {
        ticketServiceClient.updatePipelineStatus(execution.getTicketId(), PipelineStatusUpdateDTO.builder()
                .pipelineExecutionId(execution.getId())
                .environment(execution.getEnvironment())
                .status(status)
                .build());
    }

    private void persistStages(PipelineExecution execution) {
        List<GitHubJobDTO> jobs = gitHubActionsClient.getRunJobs(execution.getGithubRunId());
        stageRepository.deleteByExecutionId(execution.getId());

        int order = 1;
        for (GitHubJobDTO job : jobs) {
            stageRepository.save(PipelineStage.builder()
                    .executionId(execution.getId())
                    .name(job.getName())
                    .status(mapJobStatus(job))
                    .durationSeconds(computeDurationSeconds(job))
                    .stageOrder(order++)
                    .logsUrl(job.getHtmlUrl())
                    .build());
        }
    }

    private Integer computeDurationSeconds(GitHubJobDTO job) {
        if (job.getStartedAt() == null || job.getCompletedAt() == null) {
            return null;
        }
        return (int) Duration.between(job.getStartedAt(), job.getCompletedAt()).getSeconds();
    }

    private PipelineStatus mapJobStatus(GitHubJobDTO job) {
        if (job.getConclusion() != null) {
            return mapConclusion(job.getConclusion());
        }
        return "in_progress".equals(job.getStatus()) ? PipelineStatus.RUNNING : PipelineStatus.QUEUED;
    }

    private PipelineStatus mapConclusion(String conclusion) {
        if (conclusion == null) {
            return PipelineStatus.FAILED;
        }
        return switch (conclusion) {
            case "success" -> PipelineStatus.SUCCESS;
            case "cancelled" -> PipelineStatus.CANCELLED;
            default -> PipelineStatus.FAILED;
        };
    }

    private PipelineExecution findExecutionOrThrow(Long id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PipelineExecution", "id", id));
    }

    private PipelineExecutionDTO toDTO(PipelineExecution execution) {
        return PipelineExecutionDTO.builder()
                .id(execution.getId())
                .ticketId(execution.getTicketId())
                .githubRunId(execution.getGithubRunId())
                .environment(execution.getEnvironment())
                .status(execution.getStatus().name())
                .workflowName(execution.getWorkflowName())
                .triggerType(execution.getTriggerType())
                .triggeredByUserId(execution.getTriggeredByUserId())
                .gitBranch(execution.getGitBranch())
                .gitCommitSha(execution.getGitCommitSha())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .build();
    }

    private PipelineStageDTO toDTO(PipelineStage stage) {
        return PipelineStageDTO.builder()
                .name(stage.getName())
                .status(stage.getStatus().name())
                .durationSeconds(stage.getDurationSeconds())
                .stageOrder(stage.getStageOrder())
                .logsUrl(stage.getLogsUrl())
                .build();
    }
}
