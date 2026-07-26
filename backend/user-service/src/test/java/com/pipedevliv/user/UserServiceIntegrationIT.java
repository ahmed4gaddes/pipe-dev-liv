package com.pipedevliv.user;

import com.pipedevliv.user.entity.UserProfile;
import com.pipedevliv.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance UserProfile contre une vraie Postgres (Testcontainers), pas H2 — couvre le mapping
 * JPA réel (contrainte unique keycloak_id, colonnes, PrePersist) que @DataJpaTest (H2) peut
 * masquer. Ne passe pas par RabbitMQ (voir UserServiceImpl.syncCurrentUser) : ce test appelle le
 * repository directement, la publication d'événement est déjà couverte en unitaire.
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class UserServiceIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserProfileRepository repository;

    @Test
    void persistAndFetch_roundTripsThroughRealPostgres() {
        UserProfile saved = repository.save(UserProfile.builder()
                .keycloakId("kc-it-1")
                .email("it@example.com")
                .fullName("Integration Test")
                .roles("ROLE_DEVELOPER")
                .build());

        Optional<UserProfile> found = repository.findByKeycloakId("kc-it-1");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getEmail()).isEqualTo("it@example.com");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void keycloakId_uniqueConstraint_isEnforcedByRealPostgres() {
        repository.saveAndFlush(UserProfile.builder().keycloakId("kc-it-dup").email("a@example.com").build());

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(UserProfile.builder().keycloakId("kc-it-dup").email("b@example.com").build()));
    }
}
