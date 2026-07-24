package com.pipedevliv.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reçu de Pipeline Service (appel Feign interne, identité système) une fois qu'un déploiement
 * GitHub Actions est terminé, pour répercuter le résultat sur le ticket correspondant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStatusUpdateDTO {
    private Long pipelineExecutionId;
    private String environment;
    private String status; // "SUCCESS" ou "FAILED"
}
