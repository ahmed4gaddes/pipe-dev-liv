package com.pipedevliv.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Mirroir du DTO côté ticket-service (dto.PipelineStatusUpdateDTO) — corps du PATCH envoyé
// via TicketServiceClient.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStatusUpdateDTO {
    private Long pipelineExecutionId;
    private String environment;
    private String status;
}
