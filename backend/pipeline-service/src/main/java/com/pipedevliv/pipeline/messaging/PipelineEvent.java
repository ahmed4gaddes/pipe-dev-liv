package com.pipedevliv.pipeline.messaging;

import com.pipedevliv.pipeline.entity.PipelineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineEvent {
    private Long executionId;
    private Long ticketId;
    private String environment;
    private PipelineStatus status;
    private String triggeredByUserId;
    private LocalDateTime timestamp;
}
