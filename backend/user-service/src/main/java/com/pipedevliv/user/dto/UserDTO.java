package com.pipedevliv.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String keycloakId;
    private String email;
    private String fullName;
    private String roles;
    private LocalDateTime createdAt;
}
