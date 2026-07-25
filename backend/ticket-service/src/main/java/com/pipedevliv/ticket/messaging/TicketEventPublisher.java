package com.pipedevliv.ticket.messaging;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.entity.Ticket;
import com.pipedevliv.ticket.entity.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TicketEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishTicketCreated(TicketResponseDTO ticket) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.TICKET_CREATED, ticket);
    }

    public void publishStatusChanged(Ticket ticket, TicketStatus oldStatus, String changedByUserId) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.TICKET_STATUS_CHANGED, toEvent(ticket, oldStatus, changedByUserId));
    }

    public void publishApproved(Ticket ticket, TicketStatus oldStatus, String changedByUserId) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.TICKET_APPROVED, toEvent(ticket, oldStatus, changedByUserId));
    }

    private TicketEvent toEvent(Ticket ticket, TicketStatus oldStatus, String changedByUserId) {
        return TicketEvent.builder()
                .ticketId(ticket.getId())
                .title(ticket.getTitle())
                .oldStatus(oldStatus)
                .newStatus(ticket.getStatus())
                .changedByUserId(changedByUserId)
                .createdByUserId(ticket.getCreatedByUserId())
                .assignedToUserId(ticket.getAssignedToUserId())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
