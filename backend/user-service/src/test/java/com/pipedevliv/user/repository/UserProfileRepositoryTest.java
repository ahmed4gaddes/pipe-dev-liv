package com.pipedevliv.user.repository;

import com.pipedevliv.user.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class UserProfileRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserProfileRepository repository;

    @Test
    void findByKeycloakId_whenExists_returnsProfile() {
        entityManager.persistAndFlush(UserProfile.builder()
                .keycloakId("kc-42").email("test@example.com").fullName("Test User").roles("ROLE_DEVELOPER")
                .build());

        Optional<UserProfile> result = repository.findByKeycloakId("kc-42");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByKeycloakId_whenMissing_returnsEmpty() {
        assertThat(repository.findByKeycloakId("does-not-exist")).isEmpty();
    }

    @Test
    void keycloakId_mustBeUnique() {
        entityManager.persistAndFlush(UserProfile.builder().keycloakId("kc-dup").email("a@example.com").build());

        // Passe par le repository (proxy Spring Data) plutôt que l'EntityManager brut,
        // pour bénéficier de la traduction d'exception Spring (PersistenceExceptionTranslation).
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(UserProfile.builder().keycloakId("kc-dup").email("b@example.com").build()));
    }
}
