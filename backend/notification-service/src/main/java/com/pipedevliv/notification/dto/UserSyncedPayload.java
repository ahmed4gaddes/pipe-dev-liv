package com.pipedevliv.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Miroir minimal de {@code UserDTO} (user-service), reçu sur la routing key
 * {@code user.synced}. Alimente le read-model local {@code LocalUser}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSyncedPayload {
    private String keycloakId;
    private String email;
    private String fullName;
    private String roles;
}
