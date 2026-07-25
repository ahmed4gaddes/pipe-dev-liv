package com.pipedevliv.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Miroir de {@code UserDTO} (user-service), reçu sur la routing key {@code user.synced}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSyncedPayload {
    private Long id;
    private String keycloakId;
    private String email;
    private String fullName;
    private String roles;
    private LocalDateTime createdAt;
}
