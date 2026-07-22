package com.pipedevliv.ticket.dto;

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
public class TicketHistoryDTO {
    private Long id;
    private Long ticketId;
    private String changedByUserId;
    private TicketStatus oldStatus;
    private TicketStatus newStatus;
    private String comment;
    private LocalDateTime changedAt;
}
