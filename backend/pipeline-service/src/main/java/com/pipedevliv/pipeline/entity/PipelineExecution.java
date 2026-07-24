package com.pipedevliv.pipeline.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    // Nullable : l'API GitHub workflow_dispatch ne renvoie pas d'ID de run à la création ;
    // rempli en best-effort juste après le déclenchement (voir GitHubActionsClient.findLatestRunId).
    @Column(name = "github_run_id")
    private Long githubRunId;

    @Column(nullable = false, length = 20)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineStatus status;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "trigger_type", length = 20)
    private String triggerType;

    @Column(name = "triggered_by_user_id", nullable = false)
    private String triggeredByUserId;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "git_commit_sha")
    private String gitCommitSha;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }
}
