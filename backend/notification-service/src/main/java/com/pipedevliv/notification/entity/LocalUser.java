package com.pipedevliv.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-model local, alimenté par la consommation de l'événement {@code user.synced} — pas
 * la source de vérité (User Service l'est). Ne contient que les utilisateurs qui se sont
 * déjà connectés au moins une fois (voir explication_phase_6.md).
 */
@Entity
@Table(name = "local_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(length = 500)
    private String roles;
}
