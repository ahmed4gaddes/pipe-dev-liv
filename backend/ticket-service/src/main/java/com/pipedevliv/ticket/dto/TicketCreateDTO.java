package com.pipedevliv.ticket.dto;

import com.pipedevliv.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateDTO {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private TicketPriority priority;
    private String targetEnvironment;
    private String gitBranch;
    private String gitCommitSha;
}
