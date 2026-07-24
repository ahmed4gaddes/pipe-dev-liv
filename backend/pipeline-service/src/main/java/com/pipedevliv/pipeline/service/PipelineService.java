package com.pipedevliv.pipeline.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.pipeline.dto.GitHubWebhookPayload;
import com.pipedevliv.pipeline.dto.PipelineExecutionDTO;
import com.pipedevliv.pipeline.dto.PipelineStageDTO;
import com.pipedevliv.pipeline.dto.PipelineTriggerDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PipelineService {

    PipelineExecutionDTO triggerPipeline(PipelineTriggerDTO dto, String triggeredByUserId);

    PipelineExecutionDTO getExecution(Long id);

    PageResponse<PipelineExecutionDTO> listExecutions(Pageable pageable);

    List<PipelineExecutionDTO> listByTicket(Long ticketId);

    List<PipelineStageDTO> getStages(Long executionId);

    String getLogsUrl(Long executionId);

    void handleWorkflowRunEvent(GitHubWebhookPayload payload);
}
