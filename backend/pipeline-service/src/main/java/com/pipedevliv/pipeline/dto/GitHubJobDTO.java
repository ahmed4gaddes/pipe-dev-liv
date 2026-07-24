package com.pipedevliv.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Sous-ensemble de la réponse GitHub pour un job (GET .../actions/runs/{id}/jobs). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubJobDTO {
    private Long id;
    private String name;
    private String status;
    private String conclusion;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("started_at")
    private OffsetDateTime startedAt;

    @JsonProperty("completed_at")
    private OffsetDateTime completedAt;
}
