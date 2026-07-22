package com.pipedevliv.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps optionnel pour les actions qui n'ont besoin que d'un commentaire (ex: /reject).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketActionCommentDTO {
    private String comment;
}
