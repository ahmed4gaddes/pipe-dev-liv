package com.pipedevliv.ticket.feign;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.ticket.dto.PipelineExecutionDTO;
import com.pipedevliv.ticket.dto.PipelineTriggerDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Pipeline Service (Phase 5) n'existe pas encore : ce contrat est spéculatif et devra être
 * confirmé/ajusté une fois Pipeline Service réellement implémenté. En attendant,
 * PipelineServiceFallback répond de façon explicite plutôt que de laisser une
 * FeignException / erreur de connexion remonter brute au client.
 */
@FeignClient(name = "PIPELINE-SERVICE", fallback = PipelineServiceFallback.class)
public interface PipelineServiceClient {

    @PostMapping("/api/pipelines/trigger")
    ApiResponse<PipelineExecutionDTO> triggerPipeline(@RequestBody PipelineTriggerDTO request);

    @GetMapping("/api/pipelines/executions/{id}")
    ApiResponse<PipelineExecutionDTO> getExecution(@PathVariable Long id);
}
