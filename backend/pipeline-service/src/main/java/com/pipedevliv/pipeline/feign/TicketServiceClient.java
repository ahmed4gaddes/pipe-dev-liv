package com.pipedevliv.pipeline.feign;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.pipeline.dto.PipelineStatusUpdateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "TICKET-SERVICE")
public interface TicketServiceClient {

    @PatchMapping("/api/tickets/{id}/pipeline-status")
    ApiResponse<Object> updatePipelineStatus(@PathVariable("id") Long ticketId, @RequestBody PipelineStatusUpdateDTO dto);
}
