package com.pipedevliv.ticket.dto;

import com.pipedevliv.ticket.entity.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketUpdateDTO {
    private String title;
    private String description;
    private TicketPriority priority;
    private String targetEnvironment;
    private String gitBranch;
    private String gitCommitSha;
    private String assignedToUserId;
}
