package com.pipedevliv.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Enveloppe de GET .../actions/workflows/{file}/runs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRunsListResponse {
    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("workflow_runs")
    private List<GitHubRunDTO> workflowRuns;
}
