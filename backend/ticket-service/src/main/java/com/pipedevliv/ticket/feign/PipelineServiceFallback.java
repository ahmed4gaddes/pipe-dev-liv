package com.pipedevliv.ticket.feign;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.ticket.dto.PipelineExecutionDTO;
import com.pipedevliv.ticket.dto.PipelineTriggerDTO;
import org.springframework.stereotype.Component;

@Component
public class PipelineServiceFallback implements PipelineServiceClient {

    @Override
    public ApiResponse<PipelineExecutionDTO> triggerPipeline(PipelineTriggerDTO request) {
        throw new BusinessException("Pipeline Service indisponible");
    }

    @Override
    public ApiResponse<PipelineExecutionDTO> getExecution(Long id) {
        throw new BusinessException("Pipeline Service indisponible");
    }
}
