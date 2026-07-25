package com.pipedevliv.notification.repository;

import com.pipedevliv.notification.entity.LocalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalUserRepository extends JpaRepository<LocalUser, Long> {

    Optional<LocalUser> findByKeycloakId(String keycloakId);
}
