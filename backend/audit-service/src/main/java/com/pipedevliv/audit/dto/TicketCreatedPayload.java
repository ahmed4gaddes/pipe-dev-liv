package com.pipedevliv.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Miroir de {@code TicketResponseDTO} (ticket-service), reçu sur la routing key
 * {@code ticket.created}. Voir docs/adr/0002-rabbitmq-mirror-dto-pattern.md pour le type-mapping
 * (identique à celui introduit par notification-service en Phase 6).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketCreatedPayload {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String targetEnvironment;
    private String gitBranch;
    private String gitCommitSha;
    private String createdByUserId;
    private String assignedToUserId;
    private String approvedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
