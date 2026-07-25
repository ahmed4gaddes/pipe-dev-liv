package com.pipedevliv.ticket.messaging;

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
public class TicketEvent {
    private Long ticketId;
    private String title;
    private TicketStatus oldStatus;
    private TicketStatus newStatus;
    private String changedByUserId;
    private String createdByUserId;
    private String assignedToUserId;
    private LocalDateTime timestamp;
}
