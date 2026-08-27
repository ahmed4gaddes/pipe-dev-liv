package com.pipedevliv.pipeline.service;

import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.pipeline.dto.GitHubJobDTO;
import com.pipedevliv.pipeline.dto.GitHubRunDTO;
import com.pipedevliv.pipeline.dto.GitHubWebhookPayload;
import com.pipedevliv.pipeline.dto.PipelineExecutionDTO;
import com.pipedevliv.pipeline.dto.PipelineStatusUpdateDTO;
import com.pipedevliv.pipeline.dto.PipelineTriggerDTO;
import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import com.pipedevliv.pipeline.feign.TicketServiceClient;
import com.pipedevliv.pipeline.messaging.PipelineEventPublisher;
import com.pipedevliv.pipeline.repository.PipelineExecutionRepository;
import com.pipedevliv.pipeline.repository.PipelineStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceImplTest {

    @Mock
    private PipelineExecutionRepository executionRepository;
    @Mock
    private PipelineStageRepository stageRepository;
    @Mock
    private GitHubActionsClient gitHubActionsClient;
    @Mock
    private PipelineEventPublisher eventPublisher;
    @Mock
    private TicketServiceClient ticketServiceClient;

    private PipelineServiceImpl pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new PipelineServiceImpl(
                executionRepository, stageRepository, gitHubActionsClient, eventPublisher, ticketServiceClient);
    }

    @Test
    void triggerPipeline_createsExecution_callsGitHub_publishesStarted() {
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> {
            PipelineExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
        when(gitHubActionsClient.findLatestRunId("main")).thenReturn(Optional.of(999L));

        PipelineTriggerDTO dto = PipelineTriggerDTO.builder()
                .ticketId(5L).targetEnvironment("DEV").gitBranch("main").gitCommitSha("abc123").build();

        PipelineExecutionDTO result = pipelineService.triggerPipeline(dto, "tl-1");

        assertThat(result.getStatus()).isEqualTo("QUEUED");
        assertThat(result.getGithubRunId()).isEqualTo(999L);
        verify(gitHubActionsClient).triggerWorkflow(eq("main"), any());
        verify(eventPublisher).publishStarted(any(PipelineExecution.class));
    }

    @Test
    void triggerPipeline_runIdNotFoundYet_leavesGithubRunIdNull() {
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> {
            PipelineExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
        when(gitHubActionsClient.findLatestRunId(any())).thenReturn(Optional.empty());

        PipelineTriggerDTO dto = PipelineTriggerDTO.builder()
                .ticketId(5L).targetEnvironment("DEV").gitBranch("main").build();

        PipelineExecutionDTO result = pipelineService.triggerPipeline(dto, "tl-1");

        assertThat(result.getGithubRunId()).isNull();
    }

    /**
     * Une branche saisie avec une espace parasite doit être nettoyée avant l'appel à GitHub :
     * envoyée telle quelle, elle provoque un 422 "No ref found for: develop " côté API, remonté
     * à l'utilisateur sous une forme sans rapport visible avec la cause.
     */
    @Test
    void triggerPipeline_branchWithSurroundingWhitespace_isTrimmedBeforeCallingGitHub() {
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> {
            PipelineExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
        when(gitHubActionsClient.findLatestRunId("develop")).thenReturn(Optional.of(999L));

        PipelineTriggerDTO dto = PipelineTriggerDTO.builder()
                .ticketId(5L).targetEnvironment("DEV").gitBranch("  develop  ").build();

        PipelineExecutionDTO result = pipelineService.triggerPipeline(dto, "tl-1");

        verify(gitHubActionsClient).triggerWorkflow(eq("develop"), any());
        assertThat(result.getGitBranch()).isEqualTo("develop");
    }

    @Test
    void triggerPipeline_blankBranch_fallsBackToMain() {
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> {
            PipelineExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
        when(gitHubActionsClient.findLatestRunId("main")).thenReturn(Optional.empty());

        PipelineTriggerDTO dto = PipelineTriggerDTO.builder()
                .ticketId(5L).targetEnvironment("DEV").gitBranch("   ").build();

        pipelineService.triggerPipeline(dto, "tl-1");

        verify(gitHubActionsClient).triggerWorkflow(eq("main"), any());
    }

    @Test
    void getExecution_notFound_throws() {
        when(executionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipelineService.getExecution(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getLogsUrl_noGithubRunId_throwsBusinessException() {
        when(executionRepository.findById(1L)).thenReturn(Optional.of(existingExecution(null, PipelineStatus.QUEUED)));

        assertThatThrownBy(() -> pipelineService.getLogsUrl(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getLogsUrl_withGithubRunId_returnsHtmlUrl() {
        when(executionRepository.findById(1L)).thenReturn(Optional.of(existingExecution(999L, PipelineStatus.RUNNING)));
        when(gitHubActionsClient.getRun(999L)).thenReturn(GitHubRunDTO.builder().htmlUrl("https://github.com/x/y/actions/runs/999").build());

        String url = pipelineService.getLogsUrl(1L);

        assertThat(url).isEqualTo("https://github.com/x/y/actions/runs/999");
    }

    @Test
    void handleWorkflowRunEvent_unknownRunId_noOp() {
        when(executionRepository.findByGithubRunId(999L)).thenReturn(Optional.empty());

        pipelineService.handleWorkflowRunEvent(payload("completed", 999L, "success"));

        verify(executionRepository, never()).save(any());
        verify(eventPublisher, never()).publishCompleted(any());
    }

    @Test
    void handleWorkflowRunEvent_inProgress_updatesToRunning() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.QUEUED);
        when(executionRepository.findByGithubRunId(999L)).thenReturn(Optional.of(execution));

        GitHubWebhookPayload payload = GitHubWebhookPayload.builder()
                .action("in_progress")
                .workflowRun(GitHubWebhookPayload.WorkflowRun.builder().id(999L).status("in_progress").build())
                .build();

        pipelineService.handleWorkflowRunEvent(payload);

        verify(executionRepository).save(execution);
        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        verify(ticketServiceClient, never()).updatePipelineStatus(anyLong(), any());
    }

    @Test
    void handleWorkflowRunEvent_completedSuccess_updatesStatusPersistsStagesAndNotifiesTicket() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByGithubRunId(999L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gitHubActionsClient.getRunJobs(999L)).thenReturn(List.of(
                GitHubJobDTO.builder().name("build").status("completed").conclusion("success")
                        .startedAt(OffsetDateTime.now().minusMinutes(2)).completedAt(OffsetDateTime.now()).build()));

        pipelineService.handleWorkflowRunEvent(payload("completed", 999L, "success"));

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.SUCCESS);
        assertThat(execution.getCompletedAt()).isNotNull();
        verify(stageRepository).deleteByExecutionId(execution.getId());
        verify(stageRepository).save(any());
        verify(eventPublisher).publishCompleted(execution);
        verify(ticketServiceClient).updatePipelineStatus(eq(execution.getTicketId()), any());
    }

    @Test
    void handleWorkflowRunEvent_completedFailure_publishesFailedAndNotifiesTicket() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByGithubRunId(999L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gitHubActionsClient.getRunJobs(999L)).thenReturn(List.of());

        pipelineService.handleWorkflowRunEvent(payload("completed", 999L, "failure"));

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.FAILED);
        verify(eventPublisher).publishFailed(execution);
        verify(ticketServiceClient).updatePipelineStatus(eq(execution.getTicketId()), any());
    }

    /**
     * Un run annulé doit sortir le ticket de DEPLOYING_* comme un échec : sans notification,
     * le ticket restait bloqué en "Déploiement ..." indéfiniment (aucune autre transition ne
     * l'en sort). Le libellé "CANCELLED" est transmis tel quel pour rester exact dans
     * l'historique du ticket, même si ticket-service le mappe vers FAILED.
     */
    @Test
    void handleWorkflowRunEvent_completedCancelled_notifiesTicketSoItLeavesDeployingState() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByGithubRunId(999L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gitHubActionsClient.getRunJobs(999L)).thenReturn(List.of());

        pipelineService.handleWorkflowRunEvent(payload("completed", 999L, "cancelled"));

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.CANCELLED);
        verify(eventPublisher).publishFailed(execution);
        verify(eventPublisher, never()).publishCompleted(any());

        ArgumentCaptor<PipelineStatusUpdateDTO> captor = ArgumentCaptor.forClass(PipelineStatusUpdateDTO.class);
        verify(ticketServiceClient).updatePipelineStatus(eq(execution.getTicketId()), captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void reconcilePendingExecutions_completedRunMissedByWebhook_getsFinalizedAndNotifiesTicket() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByStatusInAndGithubRunIdIsNotNull(List.of(PipelineStatus.QUEUED, PipelineStatus.RUNNING)))
                .thenReturn(List.of(execution));
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gitHubActionsClient.getRun(999L)).thenReturn(
                GitHubRunDTO.builder().id(999L).status("completed").conclusion("success").build());
        when(gitHubActionsClient.getRunJobs(999L)).thenReturn(List.of());

        pipelineService.reconcilePendingExecutions();

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.SUCCESS);
        verify(eventPublisher).publishCompleted(execution);
        verify(ticketServiceClient).updatePipelineStatus(eq(execution.getTicketId()), any());
    }

    @Test
    void reconcilePendingExecutions_stillRunningOnGitHub_leavesExecutionUntouched() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByStatusInAndGithubRunIdIsNotNull(List.of(PipelineStatus.QUEUED, PipelineStatus.RUNNING)))
                .thenReturn(List.of(execution));
        when(gitHubActionsClient.getRun(999L)).thenReturn(
                GitHubRunDTO.builder().id(999L).status("in_progress").build());

        pipelineService.reconcilePendingExecutions();

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        verify(executionRepository, never()).save(any());
        verify(ticketServiceClient, never()).updatePipelineStatus(anyLong(), any());
    }

    @Test
    void reconcilePendingExecutions_gitHubApiFails_doesNotThrowAndSkipsExecution() {
        PipelineExecution execution = existingExecution(999L, PipelineStatus.RUNNING);
        when(executionRepository.findByStatusInAndGithubRunIdIsNotNull(List.of(PipelineStatus.QUEUED, PipelineStatus.RUNNING)))
                .thenReturn(List.of(execution));
        when(gitHubActionsClient.getRun(999L)).thenThrow(new RuntimeException("GitHub API indisponible"));

        pipelineService.reconcilePendingExecutions();

        assertThat(execution.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        verify(executionRepository, never()).save(any());
    }

    private PipelineExecution existingExecution(Long githubRunId, PipelineStatus status) {
        return PipelineExecution.builder()
                .id(1L)
                .ticketId(5L)
                .githubRunId(githubRunId)
                .environment("DEV")
                .status(status)
                .triggeredByUserId("tl-1")
                .gitBranch("main")
                .build();
    }

    private GitHubWebhookPayload payload(String action, Long runId, String conclusion) {
        return GitHubWebhookPayload.builder()
                .action(action)
                .workflowRun(GitHubWebhookPayload.WorkflowRun.builder()
                        .id(runId).status("completed").conclusion(conclusion).build())
                .build();
    }
}
