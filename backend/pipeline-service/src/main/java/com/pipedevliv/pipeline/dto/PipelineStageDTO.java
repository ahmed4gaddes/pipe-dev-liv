package com.pipedevliv.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStageDTO {
    private String name;
    private String status;
    private Integer durationSeconds;
    private Integer stageOrder;
    private String logsUrl;
}
