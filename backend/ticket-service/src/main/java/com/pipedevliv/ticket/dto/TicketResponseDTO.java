package com.pipedevliv.ticket.dto;

import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDTO {
    private Long id;
    private String title;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private String targetEnvironment;
    private String gitBranch;
    private String gitCommitSha;
    private String createdByUserId;
    private String assignedToUserId;
    private String approvedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
