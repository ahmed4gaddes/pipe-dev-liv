package com.pipedevliv.ticket.dto;

import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketFilterDTO {
    private TicketStatus status;
    private TicketPriority priority;
    private String createdByUserId;
}
