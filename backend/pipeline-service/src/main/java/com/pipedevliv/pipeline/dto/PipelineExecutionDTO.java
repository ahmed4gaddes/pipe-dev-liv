package com.pipedevliv.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionDTO {
    private Long id;
    private Long ticketId;
    private Long githubRunId;
    private String environment;
    private String status;
    private String workflowName;
    private String triggerType;
    private String triggeredByUserId;
    private String gitBranch;
    private String gitCommitSha;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
