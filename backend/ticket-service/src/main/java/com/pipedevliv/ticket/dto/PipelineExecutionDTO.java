package com.pipedevliv.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionDTO {
    private Long id;
    private String status;
    private Long ticketId;
    private String environment;
    private LocalDateTime startedAt;
}
