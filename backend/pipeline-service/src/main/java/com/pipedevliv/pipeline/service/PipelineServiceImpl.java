package com.pipedevliv.pipeline.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.pipeline.dto.GitHubJobDTO;
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
        PipelineExecution execution = PipelineExecution.builder()
                .ticketId(dto.getTicketId())
                .environment(dto.getTargetEnvironment())
                .status(PipelineStatus.QUEUED)
                .triggerType("MANUAL")
                .triggeredByUserId(triggeredByUserId)
                .gitBranch(dto.getGitBranch())
                .gitCommitSha(dto.getGitCommitSha())
                .build();
        execution = executionRepository.save(execution);

        gitHubActionsClient.triggerWorkflow(dto.getGitBranch(), Map.of(
                "environment", dto.getTargetEnvironment() == null ? "" : dto.getTargetEnvironment().toLowerCase(),
                "ticket_id", String.valueOf(dto.getTicketId())));

        Optional<Long> runId = gitHubActionsClient.findLatestRunId(dto.getGitBranch());
        if (runId.isPresent()) {
            execution.setGithubRunId(runId.get());
            execution = executionRepository.save(execution);
        } else {
            log.warn("Impossible de corréler immédiatement le run GitHub Actions pour le ticket {} (branche {})",
                    dto.getTicketId(), dto.getGitBranch());
        }

        eventPublisher.publishStarted(execution);
        return toDTO(execution);
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
        PipelineExecution execution = executionOpt.get();

        if (!"completed".equals(payload.getAction())) {
            if ("in_progress".equals(workflowRun.getStatus())) {
                execution.setStatus(PipelineStatus.RUNNING);
                executionRepository.save(execution);
            }
            return;
        }

        PipelineStatus newStatus = mapConclusion(workflowRun.getConclusion());
        execution.setStatus(newStatus);
        execution.setCompletedAt(java.time.LocalDateTime.now());
        execution = executionRepository.save(execution);

        persistStages(execution);

        if (newStatus == PipelineStatus.SUCCESS) {
            eventPublisher.publishCompleted(execution);
            notifyTicketService(execution, "SUCCESS");
        } else if (newStatus == PipelineStatus.FAILED) {
            eventPublisher.publishFailed(execution);
            notifyTicketService(execution, "FAILED");
        } else {
            log.info("Run GitHub {} terminé avec conclusion CANCELLED : ticket {} laissé en l'état, non notifié",
                    workflowRun.getId(), execution.getTicketId());
        }
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
