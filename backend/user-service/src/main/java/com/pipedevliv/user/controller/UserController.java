package com.pipedevliv.user.controller;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.user.dto.UserDTO;
import com.pipedevliv.user.dto.UserSyncDTO;
import com.pipedevliv.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResponse<UserDTO>> getAllUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(userService.getAllUsers(pageable), "Utilisateurs récupérés");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getUserById(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserById(id), "Utilisateur récupéré");
    }

    @GetMapping("/by-keycloak-id/{keycloakId}")
    public ApiResponse<UserDTO> getUserByKeycloakId(@PathVariable String keycloakId) {
        return ApiResponse.success(userService.getUserByKeycloakId(keycloakId), "Utilisateur récupéré");
    }

    /**
     * Profil de l'utilisateur connecté. Synchronise (crée ou met à jour) le profil local
     * à partir des headers d'identité injectés par la Gateway — c'est ce mécanisme qui
     * matérialise la "synchronisation au premier login" décrite en Phase 3.
     */
    @GetMapping("/me")
    public ApiResponse<UserDTO> getCurrentUser(
            @RequestHeader(value = "X-User-Email", required = false, defaultValue = "") String email,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "") String fullName,
            @RequestHeader(value = "X-User-Roles", required = false, defaultValue = "") String roles) {

        String keycloakId = SecurityContextHolder.getContext().getAuthentication().getName();

        UserDTO user = userService.syncCurrentUser(UserSyncDTO.builder()
                .keycloakId(keycloakId)
                .email(email)
                .fullName(fullName)
                .roles(roles)
                .build());

        return ApiResponse.success(user, "Profil synchronisé");
    }
}
