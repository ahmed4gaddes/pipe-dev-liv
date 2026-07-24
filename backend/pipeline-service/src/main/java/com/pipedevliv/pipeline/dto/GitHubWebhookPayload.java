package com.pipedevliv.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sous-ensemble du payload de l'événement GitHub {@code workflow_run}
 * (https://docs.github.com/en/webhooks/webhook-events-and-payloads#workflow_run) —
 * seuls les champs réellement utilisés sont mappés, le reste est ignoré.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubWebhookPayload {

    private String action;

    @JsonProperty("workflow_run")
    private WorkflowRun workflowRun;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowRun {
        private Long id;
        private String name;
        private String status;
        private String conclusion;

        @JsonProperty("head_branch")
        private String headBranch;

        @JsonProperty("head_sha")
        private String headSha;
    }
}
