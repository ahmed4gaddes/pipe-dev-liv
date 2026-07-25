package com.pipedevliv.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Miroir minimal de {@code PipelineEvent} (pipeline-service), reçu sur les routing keys
 * {@code pipeline.started}/{@code .completed}/{@code .failed}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineEventPayload {
    private Long executionId;
    private Long ticketId;
    private String environment;
    private String status;
    private String triggeredByUserId;
}
