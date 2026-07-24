package com.pipedevliv.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Mirroir du contrat spéculatif défini côté ticket-service (feign.PipelineServiceClient) — les
// deux DTOs sont volontairement dupliqués plutôt que partagés via common-lib, chaque service
// restant maître de son propre contrat REST public (même choix que UserSummaryDTO en Phase 4).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineTriggerDTO {
    private Long ticketId;
    private String targetEnvironment;
    private String gitBranch;
    private String gitCommitSha;
}
