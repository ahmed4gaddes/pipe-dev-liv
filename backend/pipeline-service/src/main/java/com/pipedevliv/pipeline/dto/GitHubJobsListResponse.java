package com.pipedevliv.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Enveloppe de GET .../actions/runs/{id}/jobs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubJobsListResponse {
    @JsonProperty("total_count")
    private Integer totalCount;

    private List<GitHubJobDTO> jobs;
}
