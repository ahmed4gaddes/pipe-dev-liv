package com.pipedevliv.pipeline.controller;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.pipeline.dto.PipelineExecutionDTO;
import com.pipedevliv.pipeline.dto.PipelineStageDTO;
import com.pipedevliv.pipeline.dto.PipelineTriggerDTO;
import com.pipedevliv.pipeline.service.PipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final com.pipedevliv.pipeline.service.PdfReportService pdfReportService;

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('TECH_LEAD')")
    public ApiResponse<PipelineExecutionDTO> trigger(@Valid @RequestBody PipelineTriggerDTO dto) {
        return ApiResponse.success(pipelineService.triggerPipeline(dto, currentUserId()), "Pipeline déclenché");
    }

    @GetMapping("/executions")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<PageResponse<PipelineExecutionDTO>> listExecutions(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(pipelineService.listExecutions(pageable), "Exécutions récupérées");
    }

    @GetMapping("/executions/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<PipelineExecutionDTO> getExecution(@PathVariable Long id) {
        return ApiResponse.success(pipelineService.getExecution(id), "Exécution récupérée");
    }

    @DeleteMapping("/executions/{id}")
    @PreAuthorize("hasRole('TECH_LEAD')")
    public ApiResponse<Void> deleteExecution(@PathVariable Long id) {
        pipelineService.deleteExecution(id);
        return ApiResponse.success(null, "Exécution supprimée");
    }

    @GetMapping("/executions/{id}/stages")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<List<PipelineStageDTO>> getStages(@PathVariable Long id) {
        return ApiResponse.success(pipelineService.getStages(id), "Stages récupérés");
    }

    @GetMapping("/executions/{id}/logs")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<Map<String, String>> getLogsUrl(@PathVariable Long id) {
        return ApiResponse.success(Map.of("url", pipelineService.getLogsUrl(id)), "Lien des logs récupéré");
    }

    @GetMapping("/executions/by-ticket/{ticketId}")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<List<PipelineExecutionDTO>> listByTicket(@PathVariable Long ticketId) {
        return ApiResponse.success(pipelineService.listByTicket(ticketId), "Exécutions du ticket récupérées");
    }

    @GetMapping(value = "/executions/{id}/report", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('VIEWER')")
    public org.springframework.http.ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        byte[] pdfBytes = pdfReportService.generateExecutionReport(id);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "rapport_execution_" + id + ".pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return new org.springframework.http.ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
