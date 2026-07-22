package com.pipedevliv.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente les données d'identité transmises par la Gateway (headers X-User-*) au moment
 * où un utilisateur appelle /api/users/me. Sert à créer ou mettre à jour son profil local
 * lors de son premier login (voir UserServiceImpl#syncCurrentUser).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSyncDTO {
    private String keycloakId;
    private String email;
    private String fullName;
    private String roles;
}
