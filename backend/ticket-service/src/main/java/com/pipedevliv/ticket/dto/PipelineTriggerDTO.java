package com.pipedevliv.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
