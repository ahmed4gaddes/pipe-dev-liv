package com.pipedevliv.ticket.dto;

import com.pipedevliv.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusChangeDTO {
    @NotNull
    private TicketStatus newStatus;
    private String comment;
}
