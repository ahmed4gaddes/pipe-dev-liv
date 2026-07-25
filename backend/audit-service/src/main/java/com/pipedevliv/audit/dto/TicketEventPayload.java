package com.pipedevliv.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Miroir de {@code TicketEvent} (ticket-service), reçu sur les routing keys
 * {@code ticket.status-changed} et {@code ticket.approved}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketEventPayload {
    private Long ticketId;
    private String title;
    private String oldStatus;
    private String newStatus;
    private String changedByUserId;
    private String createdByUserId;
    private String assignedToUserId;
    private LocalDateTime timestamp;
}
