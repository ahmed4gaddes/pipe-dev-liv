package com.pipedevliv.ticket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copie locale minimale de UserDTO (user-service) : ticket-service ne dépend pas du
 * module user-service, seulement du contrat JSON exposé par son API. Les champs non
 * repris ici (roles, createdAt) sont simplement ignorés au désérialisation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSummaryDTO {
    private Long id;
    private String keycloakId;
    private String email;
    private String fullName;
}
