package com.pipedevliv.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Miroir minimal de {@code TicketResponseDTO} (ticket-service), reçu sur la routing key
 * {@code ticket.created}. Voir explication_phase_6.md pour le mécanisme de type-mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketCreatedPayload {
    private Long id;
    private String title;
    private String createdByUserId;
}
