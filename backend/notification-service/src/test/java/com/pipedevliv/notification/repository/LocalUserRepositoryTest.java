package com.pipedevliv.notification.repository;

import com.pipedevliv.notification.entity.LocalUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LocalUserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LocalUserRepository repository;

    @Test
    void findByKeycloakId_found() {
        entityManager.persistAndFlush(LocalUser.builder()
                .keycloakId("kc-1").email("a@x.com").fullName("A B").roles("ROLE_DEVELOPER").build());

        Optional<LocalUser> result = repository.findByKeycloakId("kc-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("a@x.com");
    }

    @Test
    void findByKeycloakId_notFound_empty() {
        assertThat(repository.findByKeycloakId("missing")).isEmpty();
    }
}
