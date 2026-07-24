package com.pipedevliv.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sous-ensemble de la réponse GitHub pour un run (GET .../actions/runs/{id}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRunDTO {
    private Long id;
    private String name;
    private String status;
    private String conclusion;

    @JsonProperty("html_url")
    private String htmlUrl;
}
